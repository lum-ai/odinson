package ai.lum.odinson.lucene.search

import java.util.{ Map => JMap, Set => JSet }
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson.lucene.search.spans._

class LookaheadQuery(
  val query: OdinsonQuery,
) extends OdinsonQuery {

  override def hashCode: Int = (query).##

  def getField(): String = query.getField()

  def toString(field: String): String = s"Lookahead(${query.toString(field)})"

  override def createWeight(searcher: IndexSearcher, needsScores: Boolean): OdinsonWeight = {
    val weight = query.createWeight(searcher, needsScores).asInstanceOf[OdinsonWeight]
    val termContexts = OdinsonQuery.getTermContexts(weight)
    new LookaheadWeight(this, searcher, termContexts, weight)
  }

  override def rewrite(reader: IndexReader): Query = {
    val rewritten = query.rewrite(reader).asInstanceOf[OdinsonQuery]
    if (query != rewritten) {
      new LookaheadQuery(rewritten)
    } else {
      super.rewrite(reader)
    }
  }

}

class LookaheadWeight(
  query: OdinsonQuery,
  searcher: IndexSearcher,
  termContexts: JMap[Term, TermContext],
  val weight: OdinsonWeight,
) extends OdinsonWeight(query, searcher, termContexts) {

  def extractTerms(terms: JSet[Term]): Unit = {
    weight.extractTerms(terms)
  }

  def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = {
    weight.extractTermContexts(contexts)
  }

  def getSpans(context: LeafReaderContext, requiredPostings: SpanWeight.Postings): OdinsonSpans = {
    val spans = weight.getSpans(context, requiredPostings)
    if (spans == null) null else new LookaheadSpans(spans)
  }

}

class LookaheadSpans(
  val spans: OdinsonSpans,
) extends OdinsonSpans {
  def nextDoc(): Int = spans.nextDoc()
  def advance(target: Int): Int = spans.advance(target)
  def docID(): Int = spans.docID()
  def nextStartPosition(): Int = spans.nextStartPosition()
  def startPosition(): Int = spans.startPosition()
  def endPosition(): Int = spans.startPosition() // zero-width match
  def cost(): Long = spans.cost()
  def collect(collector: SpanCollector): Unit = spans.collect(collector)
  def positionsCost(): Float = spans.positionsCost()
  override def asTwoPhaseIterator(): TwoPhaseIterator = spans.asTwoPhaseIterator()
  override def width(): Int = spans.width()
}
