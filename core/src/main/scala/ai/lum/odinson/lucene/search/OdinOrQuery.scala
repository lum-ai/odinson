package ai.lum.odinson.lucene.search

import java.util.{List => JList, Map => JMap, Set => JSet}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuilder
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.common.JavaCollectionUtils._
import ai.lum.odinson._
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search.spans._
import ai.lum.odinson.lucene.util._
import ai.lum.odinson.state.State

class OdinOrQuery(
    val clauses: List[OdinsonQuery],
    val field: String
) extends OdinsonQuery { self =>

  override def hashCode: Int = (clauses, field).##

  def toString(field: String): String = {
    val clausesStr = clauses.map(_.toString(field)).mkString(",")
    s"OrQuery([$clausesStr])"
  }

  def getField(): String = field

  override def setState(stateOpt: Option[State]): Unit = clauses.foreach(_.setState(stateOpt))

  override def rewrite(reader: IndexReader): Query = {
    val rewritten = clauses.map(_.rewrite(reader).asInstanceOf[OdinsonQuery])
    if (clauses != rewritten) {
      new OdinOrQuery(rewritten, field)
    } else {
      super.rewrite(reader)
    }
  }

  override def createWeight(
      searcher: IndexSearcher,
      needsScores: Boolean
  ): OdinsonWeight = {
    val subWeights = clauses.map(_.createWeight(searcher, false).asInstanceOf[OdinsonWeight]).asJava
    val terms = if (needsScores) OdinsonQuery.getTermContexts(subWeights) else null
    new OdinOrWeight(subWeights, searcher, terms)
  }

  class OdinOrWeight(
      val subWeights: JList[OdinsonWeight],
      searcher: IndexSearcher,
      terms: JMap[Term, TermContext]
  ) extends OdinsonWeight(self, searcher, terms) {

    def extractTerms(terms: JSet[Term]): Unit = {
      for (weight <- subWeights) weight.extractTerms(terms)
    }

    def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = {
      for (weight <- subWeights) weight.extractTermContexts(contexts)
    }

    def getSpans(context: LeafReaderContext, requiredPostings: SpanWeight.Postings): OdinsonSpans = {
      val terms = context.reader().terms(field)
      if (terms == null) {
        return null // field does not exist
      }
      val builder = new ArrayBuilder.ofRef[OdinsonSpans]
      builder.sizeHint(clauses.size)
      for (weight <- subWeights) {
        val subSpan = weight.getSpans(context, requiredPostings)//.asInstanceOf[OdinsonSpans]
        if (subSpan != null) {
          builder += subSpan
        }
      }
      builder.result() match {
        case Array() => null
        case Array(s: OdinsonSpans) => s
        case subSpans => new OdinOrSpans(subSpans)
      }
    }

  }

}

class OdinOrSpans(val subSpans: Array[OdinsonSpans]) extends OdinsonSpans {

  private val getClauseID = subSpans.zipWithIndex.toMap

  private var topPositionSpans: OdinsonSpans = null

  private val byDocQueue = new DisiPriorityQueue(subSpans.length)
  for (spans <- subSpans) {
    byDocQueue.add(new DisiWrapper(spans))
  }

  private val byPositionQueue = new SpanPositionQueue(subSpans.length)

  def nextDoc(): Int = {
    topPositionSpans = null
    var topDocSpans = byDocQueue.top()
    val currentDoc = topDocSpans.doc
    do {
      topDocSpans.doc = topDocSpans.iterator.nextDoc()
      topDocSpans = byDocQueue.updateTop()
    } while (topDocSpans.doc == currentDoc)
    topDocSpans.doc
  }

  def advance(target: Int): Int = {
    topPositionSpans = null
    var topDocSpans = byDocQueue.top()
    do {
      topDocSpans.doc = topDocSpans.iterator.advance(target)
      topDocSpans = byDocQueue.updateTop()
    } while (topDocSpans.doc < target)
    topDocSpans.doc
  }

  def docID(): Int = byDocQueue.top().doc

  def cost(): Long = subSpans.map(_.cost()).sum

  override def asTwoPhaseIterator(): TwoPhaseIterator = {
    var sumMatchCost: Float = 0 // See also DisjunctionScorer.asTwoPhaseIterator()
    var sumApproxCost: Long = 0

    for (w <- byDocQueue.iterator().asScala) {
      if (w.twoPhaseView != null) {
        val costWeight = if (w.cost <= 1) 1 else w.cost
        sumMatchCost += w.twoPhaseView.matchCost() * costWeight
        sumApproxCost += costWeight
      }
    }

    if (sumApproxCost == 0) { // no sub spans supports approximations
      computePositionsCost()
      return null
    }

    val _matchCost = sumMatchCost / sumApproxCost

    new TwoPhaseIterator(new DisjunctionDISIApproximation(byDocQueue)) {
      def matches(): Boolean = twoPhaseCurrentDocMatches()
      def matchCost(): Float = _matchCost
    }

  }

  private var lastDocTwoPhaseMatched: Int = -1

  private def twoPhaseCurrentDocMatches(): Boolean = {
    var listAtCurrentDoc = byDocQueue.topList()
    // remove the head of the list as long as it does not match
    val currentDoc = listAtCurrentDoc.doc
    while (listAtCurrentDoc.twoPhaseView != null) {
      if (listAtCurrentDoc.twoPhaseView.matches()) {
        // use this spans for positions at current doc:
        listAtCurrentDoc.lastApproxMatchDoc = currentDoc
        // break
        lastDocTwoPhaseMatched = currentDoc
        topPositionSpans = null
        return true
      }
      // do not use this spans for positions at current doc:
      listAtCurrentDoc.lastApproxNonMatchDoc = currentDoc
      listAtCurrentDoc = listAtCurrentDoc.next
      if (listAtCurrentDoc == null) {
        return false
      }
    }
    lastDocTwoPhaseMatched = currentDoc
    topPositionSpans = null
    true
  }

  private def fillPositionQueue(): Unit = { // called at first nextStartPosition
    assert(byPositionQueue.size() == 0)
    // add all matching Spans at current doc to byPositionQueue
    var listAtCurrentDoc = byDocQueue.topList()
    while (listAtCurrentDoc != null) {
      var spansAtDoc = listAtCurrentDoc.spans.asInstanceOf[OdinsonSpans]
      if (lastDocTwoPhaseMatched == listAtCurrentDoc.doc) { // matched by DisjunctionDisiApproximation
        if (listAtCurrentDoc.twoPhaseView != null) { // matched by approximation
          if (listAtCurrentDoc.lastApproxNonMatchDoc == listAtCurrentDoc.doc) { // matches() returned false
            spansAtDoc = null
          } else {
            if (listAtCurrentDoc.lastApproxMatchDoc != listAtCurrentDoc.doc) {
              if (!listAtCurrentDoc.twoPhaseView.matches()) {
                spansAtDoc = null
              }
            }
          }
        }
      }
      if (spansAtDoc != null) {
        assert(spansAtDoc.docID() == listAtCurrentDoc.doc)
        assert(spansAtDoc.startPosition() == -1)
        spansAtDoc.nextStartPosition()
        assert(spansAtDoc.startPosition() != Spans.NO_MORE_POSITIONS)
        byPositionQueue.add(spansAtDoc)
      }
      listAtCurrentDoc = listAtCurrentDoc.next
    }
    assert(byPositionQueue.size() > 0)
  }

  def nextStartPosition(): Int = {
    if (topPositionSpans == null) {
      byPositionQueue.clear()
      fillPositionQueue() // fills byPositionQueue at first position
      topPositionSpans = byPositionQueue.top()
    } else {
      var foundNextSpan = false
      // ensure we are not repeating the previous span
      while (!foundNextSpan) {
        // remember this span
        val prevStart = startPosition()
        val prevEnd = endPosition()
        // advance to next span
        topPositionSpans.nextStartPosition()
        topPositionSpans = byPositionQueue.updateTop()
        // is the next span different from the previous one?
        foundNextSpan = startPosition() != prevStart || endPosition() != prevEnd
      }
    }
    topPositionSpans.startPosition()
  }

  def startPosition(): Int = {
    if (topPositionSpans == null) -1
    else topPositionSpans.startPosition()
  }

  def endPosition(): Int = {
    if (topPositionSpans == null) -1
    else topPositionSpans.endPosition()
  }

  override def odinsonMatch: OdinsonMatch = {
    new OrMatch(topPositionSpans.odinsonMatch, getClauseID(topPositionSpans))
  }

  override def width(): Int = topPositionSpans.width()

  def collect(collector: SpanCollector): Unit = {
    if (topPositionSpans != null) topPositionSpans.collect(collector)
  }

  private var _positionsCost: Float = -1

  private def computePositionsCost(): Unit = {
    var sumPositionsCost: Float = 0
    var sumCost: Long = 0
    for (w <- byDocQueue.iterator().asScala) {
      val costWeight: Long = if (w.cost <= 1) 1 else w.cost
      sumPositionsCost += w.spans.positionsCost() * costWeight
      sumCost += costWeight
    }
    _positionsCost = sumPositionsCost / sumCost
  }

  def positionsCost(): Float = {
    // This may be called when asTwoPhaseIterator returned null,
    // which happens when none of the sub spans supports approximations.
    assert(_positionsCost > 0)
    _positionsCost
  }

}
