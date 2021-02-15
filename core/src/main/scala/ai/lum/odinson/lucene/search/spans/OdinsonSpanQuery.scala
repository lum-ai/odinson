package ai.lum.odinson.lucene.search.spans

import java.util.{ Map => JMap, Set => JSet }
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson.lucene.search._

/** Wraps an OdinsonQuery in a SpanQuery */
class OdinsonSpanQuery(val query: OdinsonQuery) extends SpanQuery {

  override def hashCode: Int = query.hashCode

  def canEqual(a: Any): Boolean = a.isInstanceOf[OdinsonSpanQuery]

  override def equals(that: Any): Boolean = that match {
    case that: OdinsonSpanQuery => that.canEqual(this) && this.hashCode == that.hashCode
    case _                      => false
  }

  def getField(): String = query.getField()

  def toString(field: String): String = s"OdinSpanQuery(${query.toString(field)})"

  override def rewrite(reader: IndexReader): Query = {
    val rewritten = query.rewrite(reader).asInstanceOf[OdinsonQuery]
    if (rewritten != query) {
      new OdinsonSpanQuery(rewritten)
    } else {
      super.rewrite(reader)
    }
  }

  override def createWeight(searcher: IndexSearcher, needsScores: Boolean): SpanWeight = {
    val weight = query.createWeight(searcher, needsScores).asInstanceOf[OdinsonWeight]
    val termContexts = OdinsonQuery.getTermContexts(weight)
    new OdinsonSpanWeight(this, searcher, termContexts, weight)
  }

}

class OdinsonSpanWeight(
  val query: SpanQuery,
  val searcher: IndexSearcher,
  val termContexts: JMap[Term, TermContext],
  val weight: OdinsonWeight
) extends SpanWeight(query, searcher, termContexts) {

  def extractTerms(terms: JSet[Term]): Unit = weight.extractTerms(terms)

  def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = {
    weight.extractTermContexts(contexts)
  }

  def getSpans(context: LeafReaderContext, requiredPostings: SpanWeight.Postings): Spans = {
    weight.getSpans(context, requiredPostings)
  }

}
