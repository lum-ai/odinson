package ai.lum.odinson.lucene

import java.util.{ List => JList, Map => JMap, Set => JSet }
import scala.collection.JavaConverters._
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.common.JavaCollectionUtils._
import QuantifierType._

class OdinConcatQuery(
    val clauses: List[OdinQuery],
    val defaultTokenField: String,
    val sentenceLengthField: String
) extends OdinQuery { self =>

  override def hashCode: Int = mkHash(defaultTokenField, clauses)

  def toString(field: String): String = {
    val clausesStr = clauses.map(_.toString(field)).mkString(",")
    s"Concat([$clausesStr])"
  }

  def getField(): String = defaultTokenField

  override def rewrite(reader: IndexReader): Query = {
    val rewritten = clauses.map(_.rewrite(reader).asInstanceOf[OdinQuery])
    if (clauses != rewritten) {
      new OdinConcatQuery(rewritten, defaultTokenField, sentenceLengthField)
    } else {
      super.rewrite(reader)
    }
  }

  override def createWeight(searcher: IndexSearcher, needsScores: Boolean): OdinWeight = {
    val subWeights = clauses.map(_.createWeight(searcher, false).asInstanceOf[OdinWeight]).asJava
    val terms = if (needsScores) OdinQuery.getTermContexts(subWeights) else null
    new OdinConcatWeight(subWeights, searcher, terms)
  }

  class OdinConcatWeight(
      val subWeights: JList[OdinWeight],
      searcher: IndexSearcher,
      terms: JMap[Term, TermContext]
  ) extends OdinWeight(self, searcher, terms) {

    def extractTerms(terms: JSet[Term]): Unit = {
      for (weight <- subWeights) weight.extractTerms(terms)
    }

    def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = {
      for (weight <- subWeights) weight.extractTermContexts(contexts)
    }

    def getSpans(context: LeafReaderContext, requiredPostings: SpanWeight.Postings): OdinSpans = {
      val subSpans = new Array[OdinSpans](clauses.size)
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
      new OdinConcatSpans(subSpans, reader, reader.getNumericDocValues(sentenceLengthField))
    }

  }

  class OdinConcatSpans(
      val subSpans: Array[OdinSpans],
      val reader: IndexReader,
      getNumWordsPerDoc: => NumericDocValues // call-by-name
  ) extends ConjunctionSpans {

    import Spans._

    private var pq: QueueByPosition = null
    private var topPositionCaptures: List[NamedCapture] = Nil

    // only evaluate numWordsPerDoc if wildcards are involved
    private lazy val numWordsPerDoc: NumericDocValues = getNumWordsPerDoc

    override def namedCaptures: List[NamedCapture] = topPositionCaptures

    private var _groupIndex: Int = 1
    private var _groupStride: Int = 0

    override def groupIndex: Int = _groupIndex
    override def groupStride: Int = _groupStride

    def twoPhaseCurrentDocMatches(): Boolean = {
      oneExhaustedInCurrentDoc = false
      pq = QueueByPosition.mkPositionQueue(concatSpans(subSpans))
      if (pq.size() > 0) {
        atFirstInCurrentDoc = true
        topPositionCaptures = Nil
        true
      } else {
        false
      }
    }

    def nextStartPosition(): Int = {
      atFirstInCurrentDoc = false
      if (pq.size() > 0) {
        val SpanWithCaptures(span, captures, grpIndex, grpStride) = pq.pop()
        matchStart = span.start
        matchEnd = span.end
        _groupIndex = grpIndex
        _groupStride = grpStride
        topPositionCaptures = captures
      } else {
        matchStart = NO_MORE_POSITIONS
        matchEnd = NO_MORE_POSITIONS
        topPositionCaptures = Nil
      }
      matchStart
    }

    private def concatSpansPair(
        lhs: Seq[SpanWithCaptures],
        rhs: Seq[SpanWithCaptures]
    ): Seq[SpanWithCaptures] = {
      if (lhs.isEmpty || rhs.isEmpty) return Seq.empty
      val lhsGrouped = lhs.groupBy(_.span.end)
      val rhsGrouped = rhs.groupBy(_.span.start)
      val keys = (lhsGrouped.keySet intersect rhsGrouped.keySet).toArray
      for {
        k <- keys
        l <- lhsGrouped(k)
        r <- rhsGrouped(k)
        span = Span(l.span.start, r.span.end)
        captures = l.captures ++ r.captures
      } yield {
        if (r.groupStride == 1) {
          // if the rhs group is of size 1 then avoid
          // multiplying the lhs group by its stride
          SpanWithCaptures(span, captures, l.groupIndex, l.groupStride)
        } else {
          val groupIndex = l.groupIndex * l.groupStride + r.groupIndex
          val groupStride = l.groupStride * r.groupStride
          SpanWithCaptures(span, captures, groupIndex, groupStride)
        }
      }
    }

    private def concatSpans(spans: Array[OdinSpans]): Seq[SpanWithCaptures] = {
      var results: Seq[SpanWithCaptures] = null
      var numWildcards: Int = 0
      if (spans.forall(_.isInstanceOf[AllNGramsSpans])) {
        return OdinSpans.getAllSpansWithCaptures(new AllNGramsSpans(reader, numWordsPerDoc, spans.length))
      }
      for (s <- spans) {
        s match {
          // count wildcards
          case s: AllNGramsSpans =>
            numWildcards += s.n
          // unbounded ranges
          case s: OdinRangeSpans if results != null && s.spans.isInstanceOf[AllNGramsSpans] && s.max == Int.MaxValue =>
            // append wildcards if needed
            if (numWildcards > 0) {
              results = for {
                r <- results
                if r.span.end + numWildcards <= numWordsPerDoc.get(docID())
                span = Span(r.span.start, r.span.end + numWildcards)
              } yield SpanWithCaptures(span, r.captures, r.groupIndex, r.groupStride)
              numWildcards = 0
            }
            results = for {
              r <- results
              ends = r.span.end + s.min to numWordsPerDoc.get(docID()).toInt
              (end, i) <- ends.zipWithIndex
              span = Span(r.span.start, end)
              groupIndex = r.groupIndex * r.groupStride + (if (s.quantifierType == Lazy) i else ends.size - i - 1)
              groupStride = r.groupStride * ends.size
            } yield SpanWithCaptures(span, r.captures, groupIndex, groupStride)
          // optimize exact repetitions of wildcards
          case s: OdinRangeSpans if s.spans.isInstanceOf[AllNGramsSpans] && s.min == s.max =>
            numWildcards += s.spans.asInstanceOf[AllNGramsSpans].n * s.min
          // optimize ranges of wildcards
          // NOTE that results can't be null (we need to have some spans)
          case s: OdinRangeSpans if results != null && s.spans.isInstanceOf[AllNGramsSpans] =>
            // append wildcards if needed
            if (numWildcards > 0) {
              results = for {
                r <- results
                if r.span.end + numWildcards <= numWordsPerDoc.get(docID())
                span = Span(r.span.start, r.span.end + numWildcards)
              } yield SpanWithCaptures(span, r.captures, r.groupIndex, r.groupStride)
              numWildcards = 0
            }
            var newResults: Seq[SpanWithCaptures] = Vector.empty
            for (n <- s.min to s.max) {
              val rs = for {
                r <- results
                if r.span.end + n <= numWordsPerDoc.get(docID())
                span = Span(r.span.start, r.span.end + n)
              } yield SpanWithCaptures(span, r.captures, r.groupIndex, r.groupStride)
              newResults ++= rs
            }
            results = newResults
            if (results.isEmpty) return Seq.empty
          // optimize optional operator '?'
          // NOTE that we rely on the fact that the zero width match is the first of the subspans; this is enforced by the compiler
          // NOTE that results can't be null (we need to have some spans)
          case s: OdinOrSpans if results != null && s.subSpans.head.isInstanceOf[AllNGramsSpans] && s.subSpans.head.asInstanceOf[AllNGramsSpans].n == 0 =>
            // append wildcards if needed
            if (numWildcards > 0) {
              results = for {
                r <- results
                if r.span.end + numWildcards <= numWordsPerDoc.get(docID())
                span = Span(r.span.start, r.span.end + numWildcards)
              } yield SpanWithCaptures(span, r.captures, r.groupIndex, r.groupStride)
              numWildcards = 0
            }
            // evaluate clauses
            val originalResults = results
            for (clause <- s.subSpans.tail) {
              val newResults = clause match {
                case c: AllNGramsSpans =>
                  val n = c.n
                  for {
                    r <- originalResults
                    if r.span.end + n <= numWordsPerDoc.get(docID())
                    span = Span(r.span.start, r.span.end + n)
                  } yield SpanWithCaptures(span, r.captures, r.groupIndex, r.groupStride)
                case s: OdinRangeSpans if results != null && s.spans.isInstanceOf[AllNGramsSpans] && s.max == Int.MaxValue =>
                  for {
                    r <- results
                    end <- r.span.end + s.min until numWordsPerDoc.get(docID()).toInt
                    span = Span(r.span.start, end)
                  } yield SpanWithCaptures(span, r.captures, r.groupIndex, r.groupStride)
                case c =>
                  concatSpansPair(originalResults, OdinSpans.getAllSpansWithCaptures(c))
              }
              results ++= newResults
            }
          // general case
          case s =>
            if (results == null) {
              // these are our first spans
              results = OdinSpans.getAllSpansWithCaptures(s)
              // prepend wildcards if needed
              if (numWildcards > 0) {
                results = for {
                  r <- results
                  if r.span.start - numWildcards >= 0
                  span = Span(r.span.start - numWildcards, r.span.end)
                } yield SpanWithCaptures(span, r.captures, r.groupIndex, r.groupStride)
                numWildcards = 0
              }
            } else {
              // append wildcards if needed
              if (numWildcards > 0) {
                results = for {
                  r <- results
                  if r.span.end + numWildcards <= numWordsPerDoc.get(docID())
                  span = Span(r.span.start, r.span.end + numWildcards)
                } yield SpanWithCaptures(span, r.captures, r.groupIndex, r.groupStride)
                numWildcards = 0
              }
              results = concatSpansPair(results, OdinSpans.getAllSpansWithCaptures(s))
            }
            if (results.isEmpty) return Seq.empty
        }
      }
      // any remaining wildcards to append?
      if (numWildcards > 0) {
        results = for {
          r <- results
          if r.span.end + numWildcards <= numWordsPerDoc.get(docID())
          span = Span(r.span.start, r.span.end + numWildcards)
        } yield SpanWithCaptures(span, r.captures, r.groupIndex, r.groupStride)
      }
      results
    }

  }

}
