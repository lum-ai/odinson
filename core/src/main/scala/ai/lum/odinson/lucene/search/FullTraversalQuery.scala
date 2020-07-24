package ai.lum.odinson.lucene.search

import scala.annotation.tailrec
import scala.collection.mutable._
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson._
import ai.lum.odinson.state._
import ai.lum.odinson.digraph._
import ai.lum.odinson.lucene.search.spans._

case class FullTraversalQuery(
  fullTraversal: List[(GraphTraversal, OdinsonQuery)]
) {

  def setState(stateOpt: Option[State]): Unit = {
    fullTraversal.foreach { case (_, odinsonQuery) =>
      odinsonQuery.setState(stateOpt)
    }
  }

  def toString(field: String): String = {
    val traversal = fullTraversal
      .map(h => s"(${h._1}, ${h._2.toString(field)})")
      .mkString("(", ", ", ")")
    s"FullTraversalQuery($traversal)"
  }

  def createWeight(
    searcher: IndexSearcher,
    needsScores: Boolean
  ): FullTraversalWeight = {
    val allWeights = fullTraversal.map { case (g, q) =>
      val w = q.createWeight(searcher, needsScores).asInstanceOf[OdinsonWeight]
      (g, w)
    }
    FullTraversalWeight(allWeights)
  }

  def rewrite(reader: IndexReader): FullTraversalQuery = {
    val rewrittenTraversal = fullTraversal.map { case (g, q) =>
      val r = q.rewrite(reader).asInstanceOf[OdinsonQuery]
      (g, r)
    }
    if (rewrittenTraversal != fullTraversal) {
      FullTraversalQuery(rewrittenTraversal)
    } else {
      this
    }
  }

  def firstGraphTraversal: GraphTraversal = {
      fullTraversal.head._1
  }

}

case class FullTraversalWeight(
  fullTraversal: List[(GraphTraversal, OdinsonWeight)]
) {

  def getSpans(
    context: LeafReaderContext,
    requiredPostings: SpanWeight.Postings
  ): FullTraversalSpans = {
    val allSpans = fullTraversal.map { case (g, w) =>
      val s = w.getSpans(context, requiredPostings)
      // if any subspan is null, then the entire argument should fail
      if (s == null) return null
      (g, s)
    }
    FullTraversalSpans(allSpans)
  }

  def subWeights: List[OdinsonWeight] = {
    fullTraversal.map(_._2)
  }

}

case class FullTraversalSpans(
  fullTraversal: List[(GraphTraversal, OdinsonSpans)]
) {

  def subSpans: List[OdinsonSpans] = {
    val ss = fullTraversal.map(_._2)
    // if any subspan is null then the whole traversal should fail
    if (ss.exists(s => s == null)) null
    else ss
  }

  // performs the full traversal from trigger to argument
  def matchFullTraversal(
    graph: DirectedGraph,
    maxToken: Int,
    srcMatches: Array[OdinsonMatch],
  ): Array[OdinsonMatch] = {
    var currentSpans = srcMatches
    for ((traversal, spans) <- fullTraversal) {
      val dstMatches = spans.getAllMatches()
      currentSpans = matchPairs(graph, maxToken, traversal, currentSpans, dstMatches)
      if (currentSpans.isEmpty) return Array.empty
    }
    currentSpans
  }

  // advance all spans in arg to the specified doc
  def advanceToDoc(doc: Int): Boolean = {
    // try to advance all spans in fullTraversal to the current doc
    for ((traversal, spans) <- fullTraversal) {
      // if the spans haven't advanced then try to catch up
      if (spans.docID() < doc) {
        spans.advance(doc)
      }
      // if spans are beyond current doc then this arg doesn't match current doc
      if (spans.docID() > doc) { // FIXME no_more_docs?
        return false
      }
    }
    // every spans is at current doc
    true
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
