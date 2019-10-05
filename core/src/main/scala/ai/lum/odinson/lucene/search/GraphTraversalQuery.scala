package ai.lum.odinson.lucene.search

import java.util.{ Map => JMap, Set => JSet }
import scala.collection.mutable.{ ArrayBuffer, HashMap }
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson.digraph._
import ai.lum.odinson._
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search.spans._
import ai.lum.odinson.lucene.util._

/** Traverses the graph from `src` to `dst` following the traversal pattern.
 *  Returns `dst` if there is a match.
 */
class GraphTraversalQuery(
    val defaultTokenField: String,
    val dependenciesField: String,
    val src: OdinsonQuery,
    val traversal: GraphTraversal,
    val dst: OdinsonQuery
) extends OdinsonQuery { self =>

  // TODO GraphTraversal.hashCode
  override def hashCode: Int = mkHash(defaultTokenField, src, dst, traversal)

  def toString(field: String): String = {
    val s = src.toString(field)
    val d = dst.toString(field)
    val t = traversal.toString() // TODO GraphTraversal.toString
    s"GraphTraversal($s, $d, $t)"
  }

  def getField(): String = defaultTokenField

  override def rewrite(reader: IndexReader): Query = {
    val rewrittenSrc = src.rewrite(reader).asInstanceOf[OdinsonQuery]
    val rewrittenDst = dst.rewrite(reader).asInstanceOf[OdinsonQuery]
    if (src != rewrittenSrc || dst != rewrittenDst) {
      new GraphTraversalQuery(defaultTokenField, dependenciesField, rewrittenSrc, traversal, rewrittenDst)
    } else {
      super.rewrite(reader)
    }
  }

  override def createWeight(searcher: IndexSearcher, needsScores: Boolean): OdinsonWeight = {
    val srcWeight = src.createWeight(searcher, needsScores).asInstanceOf[OdinsonWeight]
    val dstWeight = dst.createWeight(searcher, needsScores).asInstanceOf[OdinsonWeight]
    val terms = if (needsScores) OdinsonQuery.getTermContexts(srcWeight, dstWeight) else null
    new GraphTraversalWeight(srcWeight, dstWeight, traversal, searcher, terms)
  }

  class GraphTraversalWeight(
      val srcWeight: OdinsonWeight,
      val dstWeight: OdinsonWeight,
      val traversal: GraphTraversal,
      searcher: IndexSearcher,
      terms: JMap[Term, TermContext]
  ) extends OdinsonWeight(self, searcher, terms) {

    def extractTerms(terms: JSet[Term]): Unit = {
      srcWeight.extractTerms(terms)
      dstWeight.extractTerms(terms)
    }

    def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = {
      srcWeight.extractTermContexts(contexts)
      dstWeight.extractTermContexts(contexts)
    }

    def getSpans(context: LeafReaderContext, requiredPostings: SpanWeight.Postings): OdinsonSpans = {
      val reader = context.reader
      val srcSpans = srcWeight.getSpans(context, requiredPostings)
      val dstSpans = dstWeight.getSpans(context, requiredPostings)
      if (srcSpans == null || dstSpans == null) return null
      val graphPerDoc = reader.getSortedDocValues(dependenciesField)
      new GraphTraversalSpans(Array(srcSpans, dstSpans), traversal, graphPerDoc)
    }

  }

}

class GraphTraversalSpans(
    val subSpans: Array[OdinsonSpans],
    val traversal: GraphTraversal,
    val graphPerDoc: SortedDocValues
) extends ConjunctionSpans {

  import Spans._

  val Array(srcSpans, dstSpans) = subSpans

  // resulting spans sorted by position
  private var pq: QueueByPosition = null

  private var topPositionOdinsonMatch: OdinsonMatch = null

  // dependency graph
  private var graph: DirectedGraph = null

  override def odinsonMatch: OdinsonMatch = topPositionOdinsonMatch

  def twoPhaseCurrentDocMatches(): Boolean = {
    oneExhaustedInCurrentDoc = false
    graph = DirectedGraph.fromBytes(graphPerDoc.get(docID()).bytes)
    pq = QueueByPosition.mkPositionQueue(matchPairs(graph, traversal, srcSpans, dstSpans))
    if (pq.size() > 0) {
      atFirstInCurrentDoc = true
      topPositionOdinsonMatch = null
      true
    } else {
      false
    }
  }

  def nextStartPosition(): Int = {
    atFirstInCurrentDoc = false
    if (pq.size() > 0) {
      topPositionOdinsonMatch = pq.pop()
      matchStart = topPositionOdinsonMatch.start
      matchEnd = topPositionOdinsonMatch.end
    } else {
      matchStart = NO_MORE_POSITIONS
      matchEnd = NO_MORE_POSITIONS
      topPositionOdinsonMatch = null
    }
    matchStart
  }

  private def mkInvIndex(spans: Seq[OdinsonMatch]): Map[Int, Seq[OdinsonMatch]] = {
    val index = HashMap.empty[Int, ArrayBuffer[OdinsonMatch]]
    for {
      s <- spans
      i <- s.tokenInterval
    } index.getOrElseUpdate(i, ArrayBuffer.empty) += s
    index.toMap.withDefaultValue(Seq.empty)
  }

  private def matchPairs(
      graph: DirectedGraph,
      traversal: GraphTraversal,
      srcSpans: OdinsonSpans,
      dstSpans: OdinsonSpans
  ): Seq[OdinsonMatch] = {
    val results: ArrayBuffer[OdinsonMatch] = ArrayBuffer.empty
    val dstIndex = mkInvIndex(dstSpans.getAllMatches())
    for (src <- srcSpans.getAllMatches()) {
      val dsts = traversal.traverseFrom(graph, src.tokenInterval)
      results ++= dsts
        .flatMap(dstIndex)
        .distinct
        .map(dst => new GraphTraversalMatch(src, dst))
    }
    results
  }

}
