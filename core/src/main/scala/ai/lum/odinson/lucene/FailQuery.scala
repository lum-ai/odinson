package ai.lum.odinson.lucene

import java.util.{ Map => JMap, Set => JSet }
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson.lucene.search._

class FailQuery(val field: String) extends OdinsonQuery { self =>

  override def hashCode: Int = mkHash(field)

  def toString(field: String): String = "FailQuery"

  def getField(): String = field

  override def createWeight(searcher: IndexSearcher, needsScores: Boolean): OdinWeight = {
    new FailWeight(searcher, null)
  }

  class FailWeight(
      searcher: IndexSearcher,
      termContexts: JMap[Term, TermContext]
  ) extends OdinWeight(self, searcher, termContexts) {

    def extractTerms(terms: JSet[Term]): Unit = ()

    def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = ()

    def getSpans(context: LeafReaderContext, requiredPostings: SpanWeight.Postings): OdinSpans = null

  }

}
