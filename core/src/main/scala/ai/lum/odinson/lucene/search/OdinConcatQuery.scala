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

  override def hashCode: Int = mkHash(defaultTokenField, clauses)

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
          } {
            // concatenate
            val concatenated = (leftSpansSorted(l), rightSpans(r)) match {
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
            // add to results
            builder += concatenated
          }
          // advance both sides to next spans
          i = iStop
          j = jStop
        }
      }
      // return results
      builder.result()
    }

    private def concatSpans(spans: Array[OdinsonSpans]): Array[OdinsonMatch] = {
      var results: Array[OdinsonMatch] = null
      // var numWildcards: Int = 0
      // if (spans.forall(_.isInstanceOf[AllNGramsSpans])) {
      //   return getAllMatches(new AllNGramsSpans(reader, numWordsPerDoc, spans.length))
      // }
      var i = 0
      while (i < spans.length) {
        val s = spans(i)
        i += 1
        s match {
          // // count wildcards
          // case s: AllNGramsSpans =>
          //   numWildcards += s.n
          // // unbounded ranges
          // case s: OdinRangeSpans if results != null && s.spans.isInstanceOf[AllNGramsSpans] && s.max == Int.MaxValue =>
          //   // append wildcards if needed
          //   if (numWildcards > 0) {
          //     results = for {
          //       r <- results
          //       if r.span.end + numWildcards <= numWordsPerDoc.get(docID())
          //     } yield SpanWithCaptures(Span(r.span.start, r.span.end + numWildcards), r.captures)
          //     numWildcards = 0
          //   }
          //   results = for {
          //     r <- results
          //     end <- r.span.end + s.min to numWordsPerDoc.get(docID()).toInt
          //   } yield SpanWithCaptures(Span(r.span.start, end), r.captures)
          // // optimize exact repetitions of wildcards
          // case s: OdinRangeSpans if s.spans.isInstanceOf[AllNGramsSpans] && s.min == s.max =>
          //   numWildcards += s.spans.asInstanceOf[AllNGramsSpans].n * s.min
          // // optimize ranges of wildcards
          // // NOTE that results can't be null (we need to have some spans)
          // case s: OdinRangeSpans if results != null && s.spans.isInstanceOf[AllNGramsSpans] =>
          //   // append wildcards if needed
          //   if (numWildcards > 0) {
          //     results = for {
          //       r <- results
          //       if r.span.end + numWildcards <= numWordsPerDoc.get(docID())
          //     } yield SpanWithCaptures(Span(r.span.start, r.span.end + numWildcards), r.captures)
          //     numWildcards = 0
          //   }
          //   var newResults: Seq[SpanWithCaptures] = Vector.empty
          //   for (n <- s.min to s.max) {
          //     val rs = for {
          //       r <- results
          //       if r.span.end + n <= numWordsPerDoc.get(docID())
          //     } yield SpanWithCaptures(Span(r.span.start, r.span.end + n), r.captures)
          //     newResults ++= rs
          //   }
          //   results = newResults
          //   if (results.isEmpty) return Seq.empty
          // // optimize optional operator '?'
          // // NOTE that we rely on the fact that the zero width match is the first of the subspans; this is enforced by the compiler
          // // NOTE that results can't be null (we need to have some spans)
          // case s: OdinOrSpans if results != null && s.subSpans.head.isInstanceOf[AllNGramsSpans] && s.subSpans.head.asInstanceOf[AllNGramsSpans].n == 0 =>
          //   // append wildcards if needed
          //   if (numWildcards > 0) {
          //     results = for {
          //       r <- results
          //       if r.span.end + numWildcards <= numWordsPerDoc.get(docID())
          //     } yield SpanWithCaptures(Span(r.span.start, r.span.end + numWildcards), r.captures)
          //     numWildcards = 0
          //   }
          //   // evaluate clauses
          //   val originalResults = results
          //   for (clause <- s.subSpans.tail) {
          //     val newResults = clause match {
          //       case c: AllNGramsSpans =>
          //         val n = c.n
          //         for {
          //           r <- originalResults
          //           if r.span.end + n <= numWordsPerDoc.get(docID())
          //         } yield SpanWithCaptures(Span(r.span.start, r.span.end + n), r.captures)
          //       case s: OdinRangeSpans if results != null && s.spans.isInstanceOf[AllNGramsSpans] && s.max == Int.MaxValue =>
          //         for {
          //           r <- results
          //           end <- r.span.end + s.min until numWordsPerDoc.get(docID()).toInt
          //         } yield SpanWithCaptures(Span(r.span.start, end), r.captures)
          //       case c =>
          //         concatSpansPair(originalResults, getAllSpansWithCaptures(c))
          //     }
          //     results ++= newResults
          //   }
          // general case
          case s =>
            if (results == null) {
              // these are our first spans
              results = s.getAllMatches()
              // prepend wildcards if needed
              // if (numWildcards > 0) {
              //   results = for {
              //     r <- results
              //     if r.start - numWildcards >= 0
              //   } yield r.copy(start = r.start - numWildcards)
              //   numWildcards = 0
              // }
            } else {
              // append wildcards if needed
              // if (numWildcards > 0) {
              //   results = for {
              //     r <- results
              //     if r.end + numWildcards <= numWordsPerDoc.get(docID())
              //   } yield r.copy(end = r.end + numWildcards)
              //   numWildcards = 0
              // }
              results = concatSpansPair(results, s.getAllMatches())
            }
            if (results.isEmpty) return Array.empty
        }
      }
      // any remaining wildcards to append?
      // if (numWildcards > 0) {
      //   results = for {
      //     r <- results
      //     if r.end + numWildcards <= numWordsPerDoc.get(docID())
      //   } yield r.copy(end = r.end + numWildcards)
      // }
      results
    }

  }

}
