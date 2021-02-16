package ai.lum.odinson.lucene.search

import java.util.{ Map => JMap, Set => JSet }
import scala.collection.mutable.ArrayBuilder
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson._
import ai.lum.odinson.digraph._
import ai.lum.odinson.state._
import ai.lum.odinson.lucene.search.spans._

case class RepetitionFullTraversalQuery(
  min: Int,
  max: Int,
  fullTraversal: FullTraversalQuery
) extends FullTraversalQuery {

  def toString(field: String): String = {
    s"RepetitionFullTraversalQuery($min, $max, ${fullTraversal.toString(field)})"
  }

  def setState(stateOpt: Option[State]): Unit = {
    fullTraversal.setState(stateOpt)
  }

  def createWeight(
    searcher: IndexSearcher,
    needsScores: Boolean
  ): FullTraversalWeight = {
    RepetitionFullTraversalWeight(min, max, fullTraversal.createWeight(searcher, needsScores))
  }

  def rewrite(reader: IndexReader): FullTraversalQuery = {
    val rewrittenFullTraversal = fullTraversal.rewrite(reader)
    if (rewrittenFullTraversal != fullTraversal) {
      RepetitionFullTraversalQuery(min, max, rewrittenFullTraversal)
    } else {
      this
    }
  }

  def firstGraphTraversal: Option[GraphTraversal] = {
    if (min == 0) None else fullTraversal.firstGraphTraversal
  }

  def lastGraphTraversalStep: Option[(GraphTraversal, OdinsonQuery)] = {
    if (min == 0) None else fullTraversal.lastGraphTraversalStep
  }

}

case class RepetitionFullTraversalWeight(
  min: Int,
  max: Int,
  fullTraversal: FullTraversalWeight
) extends FullTraversalWeight {

  def getSpans(
    context: LeafReaderContext,
    requiredPostings: SpanWeight.Postings
  ): FullTraversalSpans = {
    RepetitionFullTraversalSpans(min, max, fullTraversal.getSpans(context, requiredPostings))
  }

  def subWeights: List[OdinsonWeight] = {
    fullTraversal.subWeights
  }

  def extractTerms(terms: JSet[Term]): Unit = {
    fullTraversal.extractTerms(terms)
  }

  def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = {
    fullTraversal.extractTermContexts(contexts)
  }

}

case class RepetitionFullTraversalSpans(
  min: Int,
  max: Int,
  fullTraversal: FullTraversalSpans
) extends FullTraversalSpans {

  def subSpans: List[OdinsonSpans] = {
    if (min == 0) Nil else fullTraversal.subSpans
  }

  def advanceToDoc(doc: Int): Boolean = {
    fullTraversal.advanceToDoc(doc)
  }

  // performs the full traversal from trigger to argument
  def matchFullTraversal(
    graph: DirectedGraph,
    maxToken: Int,
    srcMatches: Array[OdinsonMatch]
  ): Array[OdinsonMatch] = {
    var currentMatches = srcMatches

    // required repetitions
    var i = 0
    while (i < min && !currentMatches.isEmpty) {
      i += 1
      currentMatches = fullTraversal.matchFullTraversal(graph, maxToken, currentMatches)
    }

    // if no current matches then we failed
    if (currentMatches.isEmpty) return currentMatches

    // optional repetitions
    val results = new ArrayBuilder.ofRef[OdinsonMatch]
    results ++= currentMatches

    while (i < max && !currentMatches.isEmpty) {
      i += 1
      currentMatches = fullTraversal.matchFullTraversal(graph, maxToken, currentMatches)
      results ++= currentMatches
    }

    // return results
    results.result().distinct
  }

}
