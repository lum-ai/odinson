package ai.lum.odinson.lucene.search

import java.util.{ Map => JMap, Set => JSet }
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson._
import ai.lum.odinson.state._
import ai.lum.odinson.digraph._
import ai.lum.odinson.lucene.search.spans._

trait FullTraversalQuery {
  def toString(field: String): String
  def setState(stateOpt: Option[State]): Unit
  def createWeight(searcher: IndexSearcher, needsScores: Boolean): FullTraversalWeight
  def rewrite(reader: IndexReader): FullTraversalQuery
  def firstGraphTraversal: Option[GraphTraversal]
  def lastGraphTraversalStep: Option[(GraphTraversal, OdinsonQuery)]
}

trait FullTraversalWeight {

  def getSpans(
    context: LeafReaderContext,
    requiredPostings: SpanWeight.Postings
  ): FullTraversalSpans

  def subWeights: List[OdinsonWeight]
  def extractTerms(terms: JSet[Term]): Unit
  def extractTermContexts(contexts: JMap[Term, TermContext]): Unit
}

trait FullTraversalSpans {
  def subSpans: List[OdinsonSpans]

  def matchFullTraversal(
    graph: DirectedGraph,
    maxToken: Int,
    srcMatches: Array[OdinsonMatch]
  ): Array[OdinsonMatch]

  def advanceToDoc(doc: Int): Boolean
}
