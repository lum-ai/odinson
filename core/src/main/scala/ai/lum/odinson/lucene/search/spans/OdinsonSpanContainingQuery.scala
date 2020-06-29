package ai.lum.odinson.lucene.search.spans

import java.util.{Map => JMap, Set => JSet}

import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson._
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search._
import ai.lum.odinson.state.State

class OdinsonSpanContainingQuery(
    val big: OdinsonQuery,   // the main query
    val little: OdinsonQuery // the filter
) extends OdinsonQuery {

  override def hashCode: Int = (big, little).##

  override def setState(stateOpt: Option[State]): Unit = {
    big.setState(stateOpt)
    little.setState(stateOpt)
  }

  def getField(): String = big.getField()

  def toString(field: String): String = {
    val b = big.toString(field)
    val l = little.toString(field)
    s"$b containing $l"
  }

  override def createWeight(searcher: IndexSearcher, needsScores: Boolean): OdinsonWeight = {
    val bigWeight = big.createWeight(searcher, false).asInstanceOf[OdinsonWeight]
    val littleWeight = little.createWeight(searcher, false).asInstanceOf[OdinsonWeight]
    val termContexts = if (needsScores) OdinsonQuery.getTermContexts(bigWeight, littleWeight) else null
    new OdinsonSpanContainingWeight(this, searcher, termContexts, bigWeight, littleWeight)
  }

  override def rewrite(reader: IndexReader): Query = {
    val rewrittenBig = big.rewrite(reader).asInstanceOf[OdinsonQuery]
    val rewrittenLittle = little.rewrite(reader).asInstanceOf[OdinsonQuery]
    if (big != rewrittenBig || little != rewrittenLittle) {
      new OdinsonSpanContainingQuery(rewrittenBig, rewrittenLittle)
    } else {
      super.rewrite(reader)
    }
  }

}

class OdinsonSpanContainingWeight(
  query: OdinsonQuery,
  searcher: IndexSearcher,
  termContexts: JMap[Term, TermContext],
  val bigWeight: OdinsonWeight,
  val littleWeight: OdinsonWeight
) extends OdinsonWeight(query, searcher, termContexts) {

  def extractTerms(terms: JSet[Term]): Unit = {
    bigWeight.extractTerms(terms)
    littleWeight.extractTerms(terms)
  }

  def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = {
    bigWeight.extractTermContexts(contexts)
    littleWeight.extractTermContexts(contexts)
  }

  def getSpans(context: LeafReaderContext, requiredPostings: SpanWeight.Postings): OdinsonSpans = {
    val bigSpans = bigWeight.getSpans(context, requiredPostings).asInstanceOf[OdinsonSpans]
    if (bigSpans == null) return null
    val littleSpans = littleWeight.getSpans(context, requiredPostings).asInstanceOf[OdinsonSpans]
    if (littleSpans == null) return null
    new OdinsonSpanContainingSpans(Array(bigSpans, littleSpans))
  }

}

class OdinsonSpanContainingSpans(
  val subSpans: Array[OdinsonSpans]
) extends ConjunctionSpans {

  import Spans._

  val Array(bigSpans, littleSpans) = subSpans

  def twoPhaseCurrentDocMatches(): Boolean = {
    oneExhaustedInCurrentDoc = false
    while (bigSpans.nextStartPosition() != NO_MORE_POSITIONS) {
      while (littleSpans.startPosition() < bigSpans.startPosition()) {
        if (littleSpans.nextStartPosition() == NO_MORE_POSITIONS) {
          oneExhaustedInCurrentDoc = true
          return false
        }
      }
      if (bigSpans.endPosition() >= littleSpans.endPosition()) {
        atFirstInCurrentDoc = true
        return true
      }
    }
    oneExhaustedInCurrentDoc = true
    false
  }

  def nextStartPosition(): Int = {
    if (atFirstInCurrentDoc) {
      atFirstInCurrentDoc = false
      matchStart = bigSpans.startPosition()
      matchEnd = bigSpans.endPosition()
      return matchStart
    }
    while (bigSpans.nextStartPosition() != NO_MORE_POSITIONS) {
      while (littleSpans.startPosition() < bigSpans.startPosition()) {
        if (littleSpans.nextStartPosition() == NO_MORE_POSITIONS) {
          oneExhaustedInCurrentDoc = true
          matchStart = NO_MORE_POSITIONS
          matchEnd = NO_MORE_POSITIONS
          return NO_MORE_POSITIONS
        }
      }
      if (bigSpans.endPosition() >= littleSpans.endPosition()) {
        matchStart = bigSpans.startPosition()
        matchEnd = bigSpans.endPosition()
        return matchStart
      }
    }
    oneExhaustedInCurrentDoc = true
    matchStart = NO_MORE_POSITIONS
    matchEnd = NO_MORE_POSITIONS
    NO_MORE_POSITIONS
  }

  override def odinsonMatch: OdinsonMatch = bigSpans.odinsonMatch

}
