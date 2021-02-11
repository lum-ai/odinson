package ai.lum.odinson.lucene.search

import java.util.{ Map => JMap, Set => JSet }
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson._
import ai.lum.odinson.lucene.search.spans._

class OdinsonOptionalQuery(
  val query: OdinsonQuery,
  val sentenceLengthField: String,
  val isGreedy: Boolean
) extends OdinsonQuery { self =>

  override def hashCode: Int = (query, sentenceLengthField, isGreedy).##

  def toString(field: String): String = {
    val q = query.toString(field)
    s"Optional($q)"
  }

  def getField(): String = query.getField()

  override def rewrite(reader: IndexReader): Query = {
    val rewritten = query.rewrite(reader).asInstanceOf[OdinsonQuery]
    if (query != rewritten) {
      new OdinsonOptionalQuery(rewritten, sentenceLengthField, isGreedy)
    } else {
      super.rewrite(reader)
    }
  }

  override def createWeight(
    searcher: IndexSearcher,
    needsScores: Boolean
  ): OdinsonWeight = {
    val weight =
      query.createWeight(searcher, needsScores).asInstanceOf[OdinsonWeight]
    val terms = if (needsScores) OdinsonQuery.getTermContexts(weight) else null
    new OdinsonOptionalWeight(weight, searcher, terms)
  }

  class OdinsonOptionalWeight(
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
      // construct ngram spans with n=0
      val reader = context.reader()
      val numWordsPerDoc = reader.getNumericDocValues(sentenceLengthField)
      val zeroGrams = new AllNGramsSpans(reader, numWordsPerDoc, 0)
      // get spans for internal query
      val spans = weight.getSpans(context, requiredPostings)
      if (spans == null) {
        // query didn't match -- return empty spans
        zeroGrams
      } else {
        // merge matches with empty spans
        val subSpans =
          if (isGreedy) Array(spans, zeroGrams)
          else Array(zeroGrams, spans)
        val mergedSpans = new OdinOrSpans(subSpans)
        new OdinsonOptionalSpans(spans, mergedSpans, isGreedy)
      }
    }

  }

}

class OdinsonOptionalSpans(
  // FIXME do i need the original spans?
  val originalSpans: OdinsonSpans, // original spans available
  val mergedSpans: OdinOrSpans, // original ORed with 0-grams
  val isGreedy: Boolean
) extends OdinsonSpans {

  def nextDoc(): Int = mergedSpans.nextDoc()
  def advance(target: Int): Int = mergedSpans.advance(target)
  def docID(): Int = mergedSpans.docID()
  def nextStartPosition(): Int = mergedSpans.nextStartPosition()
  def startPosition(): Int = mergedSpans.startPosition()
  def endPosition(): Int = mergedSpans.endPosition()
  def cost(): Long = mergedSpans.cost()
  def collect(collector: SpanCollector): Unit = mergedSpans.collect(collector)
  def positionsCost(): Float = mergedSpans.positionsCost()

  override def asTwoPhaseIterator(): TwoPhaseIterator =
    mergedSpans.asTwoPhaseIterator()

  override def width(): Int = mergedSpans.width()

  override def odinsonMatch: OdinsonMatch = {
    new OptionalMatch(mergedSpans.odinsonMatch, isGreedy)
  }

}
