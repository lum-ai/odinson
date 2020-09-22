package ai.lum.odinson.lucene.search

import java.util.{ Map => JMap, Set => JSet }
import scala.collection.mutable._
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson._
import ai.lum.odinson.state._
import ai.lum.odinson.digraph._
import ai.lum.odinson.lucene.search.spans._

case class SingleStepFullTraversalQuery(
  traversal: GraphTraversal,
  surface: OdinsonQuery,
) extends FullTraversalQuery {

  def toString(field: String): String = {
    s"SingleStepFullTraversalQuery($traversal, ${surface.toString(field)})"
  }

  def setState(stateOpt: Option[State]): Unit = {
    surface.setState(stateOpt)
  }

  def createWeight(
    searcher: IndexSearcher,
    needsScores: Boolean
  ): FullTraversalWeight = {
    val weight = surface.createWeight(searcher, needsScores).asInstanceOf[OdinsonWeight]
    SingleStepFullTraversalWeight(traversal, weight)
  }

  def rewrite(reader: IndexReader): FullTraversalQuery = {
    val rewrittenSurface = surface.rewrite(reader).asInstanceOf[OdinsonQuery]
    if (rewrittenSurface != surface) {
      SingleStepFullTraversalQuery(traversal, rewrittenSurface)
    } else {
      this
    }
  }

  def firstGraphTraversal: Option[GraphTraversal] = {
    Some(traversal)
  }

  def lastGraphTraversalStep: Option[(GraphTraversal, OdinsonQuery)] = {
    Some((traversal, surface))
  }

}

case class SingleStepFullTraversalWeight(
  traversal: GraphTraversal,
  weight: OdinsonWeight,
) extends FullTraversalWeight {

  def subWeights: List[OdinsonWeight] = List(weight)

  def extractTerms(terms: JSet[Term]): Unit = weight.extractTerms(terms)

  def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = weight.extractTermContexts(contexts)

  def getSpans(context: LeafReaderContext, requiredPostings: SpanWeight.Postings): FullTraversalSpans = {
    val spans = weight.getSpans(context, requiredPostings).asInstanceOf[OdinsonSpans]
    if (spans == null) return null
    SingleStepFullTraversalSpans(traversal, spans)
  }

}

case class SingleStepFullTraversalSpans(
  traversal: GraphTraversal,
  spans: OdinsonSpans,
) extends FullTraversalSpans {

  private var dstMatches: Array[OdinsonMatch] = null

  def subSpans: List[OdinsonSpans] = List(spans)

  def matchFullTraversal(graph: DirectedGraph, maxToken: Int, srcMatches: Array[OdinsonMatch]): Array[OdinsonMatch] = {
    matchPairs(graph, maxToken, traversal, srcMatches, dstMatches)
  }
  
  // advance all spans in arg to the specified doc
  def advanceToDoc(doc: Int): Boolean = {
    // if the spans haven't advanced then try to catch up
    if (spans.docID() < doc) {
      spans.advance(doc)
    }
    // if spans.docID doesn't match doc then it must have advanced beyond it
    val success = spans.docID() == doc
    if (success) {
      dstMatches = spans.getAllMatches()
    }
    success
  }

  // returns a map from token index to all matches that contain that token
  private def mkInvIndex(spans: Array[OdinsonMatch], maxToken: Int): Array[ArrayBuffer[OdinsonMatch]] = {
    val index = new Array[ArrayBuffer[OdinsonMatch]](maxToken)
    // empty buffer meant to stay empty and be reused
    val empty = new ArrayBuffer[OdinsonMatch]
    // add mentions at the corresponding token positions
    var i = 0
    while (i < spans.length) {
      val s = spans(i)
      i += 1
      var j = s.start
      while (j < s.end) {
        if (index(j) == null) {
          // make a new buffer at this position
          index(j) = new ArrayBuffer[OdinsonMatch]
        }
        index(j) += s
        j += 1
      }
    }
    // add the empty buffer everywhere else
    i = 0
    while (i < index.length) {
      if (index(i) == null) {
        index(i) = empty
      }
      i += 1
    }
    index
  }

  // performs one step in the full traversal
  private def matchPairs(
    graph: DirectedGraph,
    maxToken: Int,
    traversal: GraphTraversal,
    srcMatches: Array[OdinsonMatch],
    dstMatches: Array[OdinsonMatch]
  ): Array[OdinsonMatch] = {
    val builder = new ArrayBuilder.ofRef[OdinsonMatch]
    val dstIndex = mkInvIndex(dstMatches, maxToken)
    for (src <- srcMatches) {
      val dsts = traversal.traverseFrom(graph, src.tokenInterval)
      builder ++= dsts
        .flatMap(dstIndex)
        .distinct
        .map(dst => new GraphTraversalMatch(src, dst))
    }
    builder.result()
  }

}
