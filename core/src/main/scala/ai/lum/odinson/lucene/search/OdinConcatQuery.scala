package ai.lum.odinson.lucene.search

import java.util.{ List => JList, Map => JMap, Set => JSet }
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

class OdinConcatQuery(
    val clauses: List[OdinsonQuery],
    val defaultTokenField: String,
    val sentenceLengthField: String
) extends OdinsonQuery { self =>

  override def hashCode: Int = (clauses, defaultTokenField, sentenceLengthField).##

  def toString(field: String): String = {
    val clausesStr = clauses.map(_.toString(field)).mkString(",")
    s"Concat([$clausesStr])"
  }

  def getField(): String = defaultTokenField

  override def rewrite(reader: IndexReader): Query = {
    val rewritten = clauses.map(_.rewrite(reader).asInstanceOf[OdinsonQuery])
    if (clauses != rewritten) {
      new OdinConcatQuery(rewritten, defaultTokenField, sentenceLengthField)
    } else {
      super.rewrite(reader)
    }
  }

  override def createWeight(
    searcher: IndexSearcher,
    needsScores: Boolean
  ): OdinsonWeight = {
    val subWeights = clauses
      .map(_.createWeight(searcher, false).asInstanceOf[OdinsonWeight])
      .asJava
    val terms =
      if (needsScores) OdinsonQuery.getTermContexts(subWeights)
      else null
    new OdinConcatWeight(subWeights, searcher, terms)
  }

  class OdinConcatWeight(
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

    def getSpans(
      context: LeafReaderContext,
      requiredPostings: SpanWeight.Postings
    ): OdinsonSpans = {
      val subSpans = new Array[OdinsonSpans](clauses.size)
      var i = 0
      for (weight <- subWeights) {
        val subSpan = weight.getSpans(context, requiredPostings)
        if (subSpan == null) {
          return null // all required
        } else {
          subSpans(i) = subSpan
          i += 1
        }
      }
      val reader = context.reader
      new OdinConcatSpans(
        subSpans,
        reader,
        reader.getNumericDocValues(sentenceLengthField)
      )
    }

  }

  class OdinConcatSpans(
      val subSpans: Array[OdinsonSpans],
      val reader: IndexReader,
      getNumWordsPerDoc: => NumericDocValues // call-by-name
  ) extends ConjunctionSpans {

    import Spans._

    private var pq: QueueByPosition = null

    // only evaluate numWordsPerDoc if wildcards are involved
    private lazy val numWordsPerDoc: NumericDocValues = getNumWordsPerDoc

    private var topPositionOdinsonMatch: OdinsonMatch = null

    override def odinsonMatch: OdinsonMatch = topPositionOdinsonMatch

    def twoPhaseCurrentDocMatches(): Boolean = {
      oneExhaustedInCurrentDoc = false
      pq = QueueByPosition.mkPositionQueue(concatSpans(subSpans))
      if (pq.size() > 0) {
        atFirstInCurrentDoc = true
        topPositionOdinsonMatch = null
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
      }
      matchStart
    }

    private def concatSpansPair(
      leftSpans: Array[OdinsonMatch],
      rightSpans: Array[OdinsonMatch],
    ): Array[OdinsonMatch] = {
      // if either side is empty then there is nothing to concatenate
      if (leftSpans.isEmpty || rightSpans.isEmpty) return Array.empty
      // make array builder
      val leftLength = leftSpans.length
      val rightLength = rightSpans.length
      val maxLength = if (leftLength > rightLength) leftLength else rightLength
      val builder = new ArrayBuilder.ofRef[OdinsonMatch]
      builder.sizeHint(maxLength)
      // leftSpans and rightSpans are sorted by start
      // but we need leftSpans sorted by end
      val leftSpansSorted = leftSpans.sortBy(_.end)
      // indexes for leftSpansSorted and rightSpans
      var i = 0
      var j = 0
      // iterate over spans
      while (i < leftLength && j < rightLength) {
        val left = leftSpansSorted(i)
        val right = rightSpans(j)
        if (left.end < right.start) {
          // advance left
          i += 1
        } else if (left.end > right.start) {
          // advance right
          j += 1
        } else {
          // position to concatenate
          val pos = left.end
          // one after the last left span with end == pos
          var iStop = i
          while (iStop < leftSpansSorted.length && leftSpansSorted(iStop).end == pos) {
            iStop += 1
          }
          // one after the last right span with start == pos
          var jStop = j
          while (jStop < rightSpans.length && rightSpans(jStop).start == pos) {
            jStop += 1
          }
          // iterate over all pairs of spans that should be concatenated
          for {
            l <- i until iStop
            r <- j until jStop
          } builder += concatMatches(leftSpansSorted(l), rightSpans(r))
          // advance both sides to next spans
          i = iStop
          j = jStop
        }
      }
      // return results
      builder.result()
    }

    def concatMatches(lhs: OdinsonMatch, rhs: OdinsonMatch): OdinsonMatch = {
      (lhs, rhs) match {
        case (lhs: ConcatMatch, rhs: ConcatMatch) =>
          val subMatches = new Array[OdinsonMatch](lhs.subMatches.length + rhs.subMatches.length)
          System.arraycopy(lhs.subMatches, 0, subMatches, 0, lhs.subMatches.length)
          System.arraycopy(rhs.subMatches, 0, subMatches, lhs.subMatches.length, rhs.subMatches.length)
          new ConcatMatch(subMatches)
        case (lhs: ConcatMatch, rhs) =>
          val subMatches = new Array[OdinsonMatch](lhs.subMatches.length + 1)
          System.arraycopy(lhs.subMatches, 0, subMatches, 0, lhs.subMatches.length)
          subMatches(lhs.subMatches.length) = rhs
          new ConcatMatch(subMatches)
        case (lhs, rhs: ConcatMatch) =>
          val subMatches = new Array[OdinsonMatch](rhs.subMatches.length + 1)
          subMatches(0) = lhs
          System.arraycopy(rhs.subMatches, 0, subMatches, 1, rhs.subMatches.length)
          new ConcatMatch(subMatches)
        case (lhs, rhs) =>
          new ConcatMatch(Array(lhs, rhs))
      }
    }

    private def concatSpans(spans: Array[OdinsonSpans]): Array[OdinsonMatch] = {
      var results: Array[OdinsonMatch] = null
      var i = 0
      while (i < spans.length) {
        results = spans(i) match {
          case s if results == null => s.getAllMatches()
          case s => concatSpansPair(results, s.getAllMatches())
        }
        if (results.isEmpty) return Array.empty
        i += 1
      }
      results
    }

  }

}
