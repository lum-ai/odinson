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

  private var matches: Array[OdinsonMatch] = emptyMatchArray
  private var startIndex: Int = -1
  private var numReps: Int = -1

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
    startIndex = 0
    numReps = 0
    matches = spans.getAllMatches()
    if (getNextStretch()) {
      atFirstInCurrentDoc = true
      true
    } else {
      false
    }
  }

  private def getNextStretch(): Boolean = {
    while (startIndex < matches.length) {
      if (numReps == 0) {
        numReps += 1
      } else if (startIndex + numReps < matches.length && matches(startIndex+numReps-1).end == matches(startIndex+numReps).start) {
        numReps += 1
      } else {
        startIndex += 1
        numReps = 0
      }
      if (numReps > max || startIndex + numReps > matches.length) {
        startIndex += 1
        numReps = 0
      } else if (numReps >= min) {
        return true
      }
    }
    startIndex = -1
    numReps = -1
    false
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
    val subMatches = Arrays.copyOfRange(matches, startIndex, startIndex + numReps)
    new RepetitionMatch(subMatches, isGreedy)
  }

  def startPosition(): Int = {
    if (atFirstInCurrentDoc) -1
    else if (startIndex == -1) NO_MORE_POSITIONS
    else matches(startIndex).start
  }

  def endPosition(): Int = {
    if (atFirstInCurrentDoc) -1
    else if (startIndex == -1) NO_MORE_POSITIONS
    else matches(startIndex + numReps - 1).end
  }

  def nextStartPosition(): Int = {
    if (atFirstInCurrentDoc) {
      // we know we have a match because we checked previously
      atFirstInCurrentDoc = false
      return matches(startIndex).start
    }
    if (getNextStretch()) {
      matches(startIndex).start
    } else {
      matches = emptyMatchArray
      startIndex = -1
      numReps = -1
      NO_MORE_POSITIONS
    }
  }

}
