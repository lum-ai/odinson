package ai.lum.odinson.lucene

import java.util.{ Map => JMap, Set => JSet }
import scala.collection.mutable.ArrayBuffer
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import QuantifierType._

class OdinRangeQuery(
    val query: OdinQuery,
    val min: Int,
    val max: Int,
    val quantifierType: QuantifierType
) extends OdinQuery { self =>

  require(min > 0, "min must be positive")
  require(min <= max, "min can't be bigger than max")

  override def hashCode: Int = mkHash(query, min, max)

  def toString(field: String): String = {
      val q = query.toString(field)
      s"Repeat($q, $min, $max)"
  }

  def getField(): String = query.getField()

  override def rewrite(reader: IndexReader): Query = {
    val rewritten = query.rewrite(reader).asInstanceOf[OdinQuery]
    if (query != rewritten) {
      new OdinRangeQuery(rewritten, min, max, quantifierType)
    } else {
      super.rewrite(reader)
    }
  }

  override def createWeight(searcher: IndexSearcher, needsScores: Boolean): OdinWeight = {
    val weight = query.createWeight(searcher, false).asInstanceOf[OdinWeight]
    val terms = if (needsScores) OdinQuery.getTermContexts(weight) else null
    new OdinRangeWeight(weight, searcher, terms, quantifierType)
  }

  class OdinRangeWeight(
      val weight: OdinWeight,
      searcher: IndexSearcher,
      terms: JMap[Term, TermContext],
      val quantifierType: QuantifierType
  ) extends OdinWeight(self, searcher, terms) {

    def extractTerms(terms: JSet[Term]): Unit = {
      weight.extractTerms(terms)
    }

    def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = {
      weight.extractTermContexts(contexts)
    }

    def getSpans(context: LeafReaderContext, requiredPostings: SpanWeight.Postings): OdinSpans = {
      val spans = weight.getSpans(context, requiredPostings)
      if (spans == null) null else new OdinRangeSpans(spans, min, max, quantifierType)
    }

  }

}

class OdinRangeSpans(
    val spans: OdinSpans,
    val min: Int,
    val max: Int,
    val quantifierType: QuantifierType
) extends OdinSpans {

  import DocIdSetIterator._
  import Spans._

  // a first start position is available in current doc for nextStartPosition
  protected var atFirstInCurrentDoc: Boolean = false

  private var stretch: IndexedSeq[SpanWithCaptures] = ArrayBuffer.empty
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
    if (stretch.isEmpty) {
      spans.nextStartPosition()
      stretch = getNextStretch()
      startIndex = 0
      numReps = min
    }
    while (stretch.nonEmpty) {
      if (numReps > max || startIndex + numReps > stretch.length) {
        startIndex += 1
        numReps = min
      }
      if (startIndex + numReps <= stretch.length) {
        atFirstInCurrentDoc = true
        return true
      }
      // if we reach this point then we need a new stretch
      stretch = getNextStretch()
      startIndex = 0
      numReps = min
    }
    false
  }

  def getNextStretch(): IndexedSeq[SpanWithCaptures] = {
    while (spans.startPosition() != NO_MORE_POSITIONS) {
      val stretch = getStretch()
      if (stretch.length >= min) return stretch
    }
    IndexedSeq.empty
  }

  def getStretch(): IndexedSeq[SpanWithCaptures] = {
    var end = spans.startPosition()
    val stretch = ArrayBuffer.empty[SpanWithCaptures]
    while (spans.startPosition() == end) {
      stretch += spans.spanWithCaptures
      end = spans.endPosition()
      spans.nextStartPosition()
    }
    stretch
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

  override def namedCaptures: List[NamedCapture] = {
    stretch
      .slice(startIndex, startIndex + numReps)
      .map(_.captures)
      // the cost of concatenating two lists is given by the length
      // of the list to the left, so we want list concatenation to be
      // right-associative, which is why we use foldRight
      .foldRight(List.empty[NamedCapture])(_ ++ _)
  }

  def startPosition(): Int = {
    if (atFirstInCurrentDoc) -1
    else if (stretch.isEmpty) NO_MORE_POSITIONS
    else stretch(startIndex).span.start
  }

  def endPosition(): Int = {
    if (atFirstInCurrentDoc) -1
    else if (stretch.isEmpty) NO_MORE_POSITIONS
    else stretch(startIndex + numReps - 1).span.end
  }

  def nextStartPosition(): Int = {
    if (atFirstInCurrentDoc) {
      // we know we have a match because we checked previously
      atFirstInCurrentDoc = false
      return stretch(startIndex).span.start
    }
    while (stretch.nonEmpty) {
      numReps += 1
      if (numReps > max || startIndex + numReps > stretch.length) {
        startIndex += 1
        numReps = min
      }
      if (startIndex + numReps <= stretch.length) {
        return stretch(startIndex).span.start
      }
      // if we reach this point then we need a new stretch
      stretch = getNextStretch()
      startIndex = 0
      numReps = min - 1
    }
    NO_MORE_POSITIONS
  }

  // spans are in the same group if they start in the same position
  // the number of spans that can start in the same position is defined
  // by the size of the stretch and the max number of repetitions
  override def groupStride: Int = {
    var size = 1
    if (min < max) {
      size -= min
      size += math.min(max, stretch.length - startIndex)
    }
    size
  }

  // returns the order index of the current span in its group
  // for example, X{2,4} has 3 spans in its group and the indices are
  // 0 for the span (2,5), 1 for the span (2, 4), and 2 for the span (2,3)
  // note that this example is greedy, if it was lazy then the indices would be in reverse order
  // 0 for the span (2,3), 1 for the span (2, 4), and 2 for the span (2,5)
  override def groupIndex: Int = if (quantifierType == Lazy) numReps - min else groupStride - numReps - min

}
