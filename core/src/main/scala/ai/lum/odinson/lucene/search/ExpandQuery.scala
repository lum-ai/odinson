package ai.lum.odinson.lucene.search

import java.util.{ Map => JMap, Set => JSet }

import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson.lucene.search.spans.OdinsonSpans
import ai.lum.odinson._
import ai.lum.odinson.lucene.util.QueueByPosition
import org.apache.lucene.search.DocIdSetIterator.NO_MORE_DOCS

// TODO rename to FlattenQuery?
class ExpandQuery(val query: OdinsonQuery) extends OdinsonQuery {

  override def hashCode(): Int = {
    (query).##
  }

  def getField(): String = {
    query.getField()
  }

  def toString(field: String): String = {
    s"ExpandQuery(${query.toString(field)})"
  }

  override def createWeight(
    searcher: IndexSearcher,
    needsScores: Boolean
  ): Weight = {
    val weight =
      query.createWeight(searcher, needsScores).asInstanceOf[OdinsonWeight]
    val termContexts = OdinsonQuery.getTermContexts(weight)
    new ExpandWeight(this, searcher, termContexts, weight)
  }

  override def rewrite(reader: IndexReader): Query = {
    val rewritten = query.rewrite(reader).asInstanceOf[OdinsonQuery]
    if (query != rewritten) {
      new ExpandQuery(rewritten)
    } else {
      super.rewrite(reader)
    }
  }

}

class ExpandWeight(
  query: OdinsonQuery,
  searcher: IndexSearcher,
  termContexts: JMap[Term, TermContext],
  val weight: OdinsonWeight
) extends OdinsonWeight(query, searcher, termContexts) {

  def extractTerms(terms: JSet[Term]): Unit = {
    weight.extractTerms(terms)
  }

  def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = {
    weight.extractTermContexts(contexts)
  }

  def getSpans(
    ctx: LeafReaderContext,
    requiredPostings: SpanWeight.Postings
  ): OdinsonSpans = {
    val spans = weight.getSpans(ctx, requiredPostings)
    if (spans == null) null else new ExpandSpans(spans)
  }

}

class ExpandSpans(val spans: OdinsonSpans) extends OdinsonSpans {

  import Spans._

  private var atFirstInCurrentDoc: Boolean = true

  private var pq: QueueByPosition = null

  private var topPositionOdinsonMatch: OdinsonMatch = null

  private var matchStart: Int = -1
  private var matchEnd: Int = -1

  def nextDoc(): Int = {
    atFirstInCurrentDoc = true
    if (spans.nextDoc() == NO_MORE_DOCS) {
      NO_MORE_DOCS
    } else {
      toMatchDoc()
    }
    //spans.nextDoc()
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

  def advance(target: Int): Int = {
    atFirstInCurrentDoc = true
    if (spans.advance(target) == NO_MORE_DOCS) {
      NO_MORE_DOCS
    } else {
      toMatchDoc()
    }
  }

  def docID(): Int = {
    spans.docID()
  }

  def startPosition(): Int = {
    if (atFirstInCurrentDoc) -1 else matchStart
  }

  def endPosition(): Int = {
    if (atFirstInCurrentDoc) -1 else matchEnd
  }

  def mkMatches(): Seq[OdinsonMatch] = {
    val allMatches = spans.getAllMatches()
    val (graphMatches, otherMatches) = allMatches.partition {
      case m: GraphTraversalMatch => true
      case _                      => false
    }
    if (graphMatches.isEmpty) {
      otherMatches
    } else {
      val groupedMatches = graphMatches
        .map(_.asInstanceOf[GraphTraversalMatch])
        .groupBy(_.srcMatch)
      val expandedMatches = for {
        (startMatch, endMatches) <- groupedMatches.toSeq
        group = startMatch +: endMatches
        startToken = group.map(_.start).min
        endToken = group.map(_.end).max
      } yield new NGramMatch(startToken, endToken)
      expandedMatches ++ otherMatches
    }
  }

  def twoPhaseCurrentDocMatches(): Boolean = {
    pq = QueueByPosition.mkPositionQueue(mkMatches())
    if (pq.size() > 0) {
      atFirstInCurrentDoc = true
      true
    } else {
      false
    }
  }

  def nextStartPosition(): Int = {
    atFirstInCurrentDoc = false
    if (pq.size() > 0) {
      topPositionOdinsonMatch = pq.pop()
      matchStart = topPositionOdinsonMatch.start
      matchEnd = topPositionOdinsonMatch.end
    } else {
      matchStart = NO_MORE_POSITIONS
      matchEnd = NO_MORE_POSITIONS
      topPositionOdinsonMatch = null
    }
    matchStart
  }

  def cost(): Long = {
    spans.cost()
  }

  def collect(collector: SpanCollector): Unit = {
    spans.collect(collector)
  }

  def positionsCost(): Float = {
    // asTwoPhaseIterator never returns null
    throw new UnsupportedOperationException
  }

  override def asTwoPhaseIterator(): TwoPhaseIterator = {
    val tpi = spans.asTwoPhaseIterator()
    val totalMatchCost =
      if (tpi != null) tpi.matchCost() else spans.positionsCost()
    new TwoPhaseIterator(spans) {
      def matches(): Boolean = twoPhaseCurrentDocMatches()
      def matchCost(): Float = totalMatchCost
    }
  }

  override def width(): Int = {
    spans.width()
  }

  override def odinsonMatch: OdinsonMatch = {
    topPositionOdinsonMatch
  }

}
