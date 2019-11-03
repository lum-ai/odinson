package ai.lum.odinson.lucene.search

import java.util.{ Arrays, Map => JMap, Set => JSet }
import scala.collection.mutable.ArrayBuilder
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson._
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search.spans._

class OdinRepetitionQuery(
    val query: OdinsonQuery,
    val min: Int,
    val max: Int,
    val isGreedy: Boolean
) extends OdinsonQuery { self =>

  require(min > 0, "min must be positive")
  require(min <= max, "min can't be bigger than max")

  override def hashCode: Int = (query, min, max, isGreedy).##

  def toString(field: String): String = {
    val q = query.toString(field)
    s"Repeat($q, $min, $max)"
  }

  def getField(): String = query.getField()

  override def rewrite(reader: IndexReader): Query = {
    val rewritten = query.rewrite(reader).asInstanceOf[OdinsonQuery]
    if (query != rewritten) {
      new OdinRepetitionQuery(rewritten, min, max, isGreedy)
    } else {
      super.rewrite(reader)
    }
  }

  override def createWeight(
    searcher: IndexSearcher,
    needsScores: Boolean
  ): OdinsonWeight = {
    val weight = query.createWeight(searcher, needsScores).asInstanceOf[OdinsonWeight]
    val terms = if (needsScores) OdinsonQuery.getTermContexts(weight) else null
    new OdinRepetitionWeight(weight, searcher, terms)
  }

  class OdinRepetitionWeight(
      val weight: OdinsonWeight,
      searcher: IndexSearcher,
      terms: JMap[Term, TermContext]
  ) extends OdinsonWeight(self, searcher, terms) {

    def extractTerms(terms: JSet[Term]): Unit = {
      weight.extractTerms(terms)
    }

    def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = {
      weight.extractTermContexts(contexts)
    }

    def getSpans(
      context: LeafReaderContext,
      requiredPostings: SpanWeight.Postings
    ): OdinsonSpans = {
      val spans = weight.getSpans(context, requiredPostings)
      if (spans == null) null
      else new OdinRepetitionSpans(spans, min, max, isGreedy)
    }

  }

}

class OdinRepetitionSpans(
    val spans: OdinsonSpans,
    val min: Int,
    val max: Int,
    val isGreedy: Boolean
) extends OdinsonSpans {

  import DocIdSetIterator._
  import Spans._

  // a first start position is available in current doc for nextStartPosition
  protected var atFirstInCurrentDoc: Boolean = false

  private var stretch: Array[OdinsonMatch] = Array.empty
  private var startIndex: Int = 0
  private var numReps: Int = 0

  def cost(): Long = spans.cost()

  def docID(): Int = spans.docID()

  def nextDoc(): Int = {
    if (spans.nextDoc() == NO_MORE_DOCS) {
      NO_MORE_DOCS
    } else {
      toMatchDoc()
    }
  }

  def advance(target: Int): Int = {
    if (spans.advance(target) == NO_MORE_DOCS) {
      NO_MORE_DOCS
    } else {
      toMatchDoc()
    }
  }

  def toMatchDoc(): Int = {
    @annotation.tailrec
    def getDoc(): Int = {
      if (twoPhaseCurrentDocMatches()) {
        docID()
      } else if (spans.nextDoc() == NO_MORE_DOCS) {
        NO_MORE_DOCS
      } else {
        getDoc()
      }
    }
    getDoc()
  }

  def collect(collector: SpanCollector): Unit = spans.collect(collector)

  def twoPhaseCurrentDocMatches(): Boolean = {
    if (spans.nextStartPosition() == NO_MORE_POSITIONS) {
      return false
    }
    stretch = getNextStretch()
    if (stretch.nonEmpty) {
      atFirstInCurrentDoc = true
      startIndex = 0
      numReps = min
      true
    } else {
      false
    }
  }

  // collect all consecutive matches
  // and return them in an array
  private def getStretch(): Array[OdinsonMatch] = {
    var end = spans.startPosition()
    val builder = new ArrayBuilder.ofRef[OdinsonMatch]
    while (spans.startPosition() == end) {
      builder += spans.odinsonMatch
      end = spans.endPosition()
      spans.nextStartPosition()
    }
    builder.result()
  }

  // get the next stretch that is of size `min` or bigger
  // or return empty if there is no such a stretch in the document
  private def getNextStretch(): Array[OdinsonMatch] = {
    while (spans.startPosition() != NO_MORE_POSITIONS) {
      val stretch = getStretch()
      if (stretch.length >= min) return stretch
    }
    Array.empty
  }

  override def asTwoPhaseIterator(): TwoPhaseIterator = {
    val tpi = spans.asTwoPhaseIterator()
    val cost = if (tpi != null) tpi.matchCost() else spans.positionsCost()
    new TwoPhaseIterator(spans) {
      def matches(): Boolean = twoPhaseCurrentDocMatches()
      def matchCost(): Float = cost
    }
  }

  def positionsCost(): Float = {
    // asTwoPhaseIterator never returns null (see above)
    throw new UnsupportedOperationException
  }

  override def odinsonMatch: OdinsonMatch = {
    val subMatches = Arrays.copyOfRange(stretch, startIndex, startIndex + numReps)
    new RepetitionMatch(subMatches, isGreedy)
  }

  def startPosition(): Int = {
    if (atFirstInCurrentDoc) -1
    else if (stretch.isEmpty) NO_MORE_POSITIONS
    else stretch(startIndex).start
  }

  def endPosition(): Int = {
    if (atFirstInCurrentDoc) -1
    else if (stretch.isEmpty) NO_MORE_POSITIONS
    else stretch(startIndex + numReps - 1).end
  }

  def nextStartPosition(): Int = {
    if (atFirstInCurrentDoc) {
      // we know we have a match because we checked previously
      atFirstInCurrentDoc = false
      return stretch(startIndex).start
    }
    while (stretch.nonEmpty) {
      numReps += 1
      if (numReps > max || startIndex + numReps > stretch.length) {
        startIndex += 1
        numReps = min
      }
      if (startIndex + numReps <= stretch.length) {
        return stretch(startIndex).start
      }
      // if we reach this point then we need a new stretch
      stretch = getNextStretch()
      startIndex = 0
      numReps = min - 1
    }
    NO_MORE_POSITIONS
  }

}
