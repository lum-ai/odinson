package ai.lum.odinson.lucene

import java.util.{ List => JList, Map => JMap, Set => JSet }
import scala.collection.JavaConverters._
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.common.JavaCollectionUtils._
import ai.lum.odinson.lucene.search._

class OdinTermAndQuery(
    val clauses: List[OdinsonQuery],
    val field: String
) extends OdinsonQuery { self =>

  override def hashCode: Int = mkHash(field, clauses)

  def toString(field: String): String = {
    val clausesStr = clauses.map(_.toString(field)).mkString(",")
    s"AndQuery([$clausesStr])"
  }

  def getField(): String = field

  override def rewrite(reader: IndexReader): Query = {
    val rewritten = clauses.map(_.rewrite(reader).asInstanceOf[OdinsonQuery])
    if (clauses != rewritten) {
      new OdinTermAndQuery(rewritten, field)
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
    new OdinTermAndWeight(subWeights, searcher, terms)
  }

  class OdinTermAndWeight(
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

    def getSpans(context: LeafReaderContext, requiredPostings: SpanWeight.Postings): OdinSpans = {
      val terms = context.reader().terms(field)
      if (terms == null) {
        return null // field does not exist
      }
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
      new OdinTermAndSpans(subSpans)
    }

  }

  class OdinTermAndSpans(val subSpans: Array[OdinSpans]) extends ConjunctionSpans {

    import Spans._

    def twoPhaseCurrentDocMatches(): Boolean = {
      oneExhaustedInCurrentDoc = false
      while (subSpans(0).nextStartPosition() != NO_MORE_POSITIONS && !oneExhaustedInCurrentDoc) {
        if (ensureConjunction()) {
          atFirstInCurrentDoc = true
          return true
        }
      }
      false
    }

    def ensureConjunction(): Boolean = {
      val prevSpans = subSpans(0)
      matchStart = prevSpans.startPosition()
      matchEnd = prevSpans.endPosition()
      assert(prevSpans.startPosition() != NO_MORE_POSITIONS)
      assert(prevSpans.endPosition() != NO_MORE_POSITIONS)
      for (i <- 1 until subSpans.length) {
        val spans = subSpans(i)
        assert(spans.startPosition() != NO_MORE_POSITIONS)
        assert(spans.endPosition() != NO_MORE_POSITIONS)
        while (spans.startPosition() < matchStart) {
          spans.nextStartPosition()
        }
        if (spans.startPosition() == NO_MORE_POSITIONS) {
          oneExhaustedInCurrentDoc = true
          return false
        } else if (spans.startPosition() > matchStart) {
          return false
        } else if (spans.endPosition() != matchEnd) {
          return false
        }
      }
      true
    }

    def nextStartPosition(): Int = {
      if (atFirstInCurrentDoc) {
        atFirstInCurrentDoc = false
        return matchStart
      }
      oneExhaustedInCurrentDoc = false
      while (subSpans(0).nextStartPosition() != NO_MORE_POSITIONS && !oneExhaustedInCurrentDoc) {
        if (ensureConjunction()) return matchStart
      }
      matchStart = NO_MORE_POSITIONS
      matchEnd = NO_MORE_POSITIONS
      NO_MORE_POSITIONS
    }

  }

}
