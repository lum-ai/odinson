package ai.lum.odinson.lucene.search

import java.util.{ Map => JMap, Set => JSet }
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search.spans._

class OdinQueryNamedCapture(
    val query: OdinsonQuery,
    val captureName: String
) extends OdinsonQuery {

  override def hashCode: Int = mkHash(query, captureName)

  def getField(): String = query.getField()

  def toString(field: String): String = s"NamedCapture(${query.toString(field)},$captureName)"

  override def createWeight(searcher: IndexSearcher, needsScores: Boolean): OdinsonWeight = {
    val weight = query.createWeight(searcher, needsScores).asInstanceOf[OdinsonWeight]
    val termContexts = OdinsonQuery.getTermContexts(weight)
    new OdinWeightNamedCapture(this, searcher, termContexts, weight, captureName)
  }

  override def rewrite(reader: IndexReader): Query = {
    val rewritten = query.rewrite(reader).asInstanceOf[OdinsonQuery]
    if (query != rewritten) {
      new OdinQueryNamedCapture(rewritten, captureName)
    } else {
      super.rewrite(reader)
    }
  }

}

class OdinWeightNamedCapture(
    query: OdinsonQuery,
    searcher: IndexSearcher,
    termContexts: JMap[Term, TermContext],
    val weight: OdinsonWeight,
    val captureName: String
) extends OdinsonWeight(query, searcher, termContexts) {

  def extractTerms(terms: JSet[Term]): Unit = weight.extractTerms(terms)

  def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = {
    weight.extractTermContexts(contexts)
  }

  def getSpans(context: LeafReaderContext, requiredPostings: SpanWeight.Postings): OdinsonSpans = {
    val spans = weight.getSpans(context, requiredPostings)
    if (spans == null) null else new OdinSpansNamedCapture(spans, captureName)
  }

}

class OdinSpansNamedCapture(
    val spans: OdinsonSpans,
    val captureName: String
) extends OdinsonSpans {
  def nextDoc(): Int = spans.nextDoc()
  def advance(target: Int): Int = spans.advance(target)
  def docID(): Int = spans.docID()
  def nextStartPosition(): Int = spans.nextStartPosition()
  def startPosition(): Int = spans.startPosition()
  def endPosition(): Int = spans.endPosition()
  def cost(): Long = spans.cost()
  def collect(collector: SpanCollector): Unit = spans.collect(collector)
  def positionsCost(): Float = spans.positionsCost()
  override def asTwoPhaseIterator(): TwoPhaseIterator = spans.asTwoPhaseIterator()
  override def width(): Int = spans.width()
  override def namedCaptures: List[NamedCapture] = (captureName, span) :: spans.namedCaptures
}
