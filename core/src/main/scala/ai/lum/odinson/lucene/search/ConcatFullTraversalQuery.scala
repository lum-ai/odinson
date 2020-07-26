package ai.lum.odinson.lucene.search

import java.util.{ Map => JMap, Set => JSet }
import scala.annotation.tailrec
import scala.collection.mutable._
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson._
import ai.lum.odinson.state._
import ai.lum.odinson.digraph._
import ai.lum.odinson.lucene.search.spans._

case class ConcatFullTraversalQuery(
  fullTraversal: List[FullTraversalQuery]
) extends FullTraversalQuery {

  def setState(stateOpt: Option[State]): Unit = {
    for (t <- fullTraversal) t.setState(stateOpt)
  }

  def toString(field: String): String = {
    val traversal = fullTraversal
      .map(_.toString(field))
      .mkString("[", ", ", "]")
    s"ConcatFullTraversalQuery($traversal)"
  }

  def createWeight(
    searcher: IndexSearcher,
    needsScores: Boolean
  ): FullTraversalWeight = {
    val allWeights = fullTraversal.map(_.createWeight(searcher, needsScores))
    ConcatFullTraversalWeight(allWeights)
  }

  def rewrite(reader: IndexReader): FullTraversalQuery = {
    val rewrittenTraversal = fullTraversal.map(_.rewrite(reader))
    if (rewrittenTraversal != fullTraversal) {
      ConcatFullTraversalQuery(rewrittenTraversal)
    } else {
      this
    }
  }

  def firstGraphTraversal: Option[GraphTraversal] = {
    fullTraversal.headOption.flatMap(_.firstGraphTraversal)
  }

  def lastGraphTraversalStep: Option[(GraphTraversal, OdinsonQuery)] = {
    fullTraversal.lastOption.flatMap(_.lastGraphTraversalStep)
  }

}

case class ConcatFullTraversalWeight(
  fullTraversal: List[FullTraversalWeight]
) extends FullTraversalWeight {

  def getSpans(
    context: LeafReaderContext,
    requiredPostings: SpanWeight.Postings
  ): FullTraversalSpans = {
    val allSpans = fullTraversal.map { w =>
      val s = w.getSpans(context, requiredPostings)
      // if any subspan is null, then the entire argument should fail
      if (s == null) return null
      s
    }
    ConcatFullTraversalSpans(allSpans)
  }

  def subWeights: List[OdinsonWeight] = {
    fullTraversal.flatMap(_.subWeights)
  }

  def extractTerms(terms: JSet[Term]): Unit = {
    subWeights.foreach(_.extractTerms(terms))
  }

  def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = {
    subWeights.foreach(_.extractTermContexts(contexts))
  }

}

case class ConcatFullTraversalSpans(
  fullTraversal: List[FullTraversalSpans]
) extends FullTraversalSpans {

  def subSpans: List[OdinsonSpans] = {
    fullTraversal.flatMap { s =>
      val ss = s.subSpans
      // if any subspan is null then the whole traversal should fail
      if (ss == null) return null
      ss
    }
  }

  // performs the full traversal from trigger to argument
  def matchFullTraversal(
    graph: DirectedGraph,
    maxToken: Int,
    srcMatches: Array[OdinsonMatch],
  ): Array[OdinsonMatch] = {
    var currentMatches = srcMatches
    for (step <- fullTraversal) {
      currentMatches = step.matchFullTraversal(graph, maxToken, currentMatches)
      if (currentMatches.isEmpty) return Array.empty
    }
    currentMatches
  }

  // advance all spans in arg to the specified doc
  def advanceToDoc(doc: Int): Boolean = {
    // try to advance all spans in fullTraversal to the current doc
    for (step <- fullTraversal) {
      if (!step.advanceToDoc(doc)) return false
    }
    // every spans is at current doc
    true
  }

}
