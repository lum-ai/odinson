package ai.lum.odinson.lucene.search

import java.util.{ Map => JMap, Set => JSet }
import scala.collection.mutable.{ ArrayBuilder, ArrayBuffer, HashMap, HashSet }
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson.digraph._
import ai.lum.odinson._
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search.spans._
import ai.lum.odinson.lucene.util._
import ai.lum.odinson.serialization.UnsafeSerializer

/** Traverses the graph from `src` to `dst` following the traversal pattern.
 *  Returns `dst` if there is a match.
 */
class GraphTraversalQuery(
    val defaultTokenField: String,
    val dependenciesField: String,
    val sentenceLengthField: String,
    val src: OdinsonQuery,
    val traversal: GraphTraversal,
    val dst: OdinsonQuery
) extends OdinsonQuery { self =>

  // TODO GraphTraversal.hashCode
  override def hashCode: Int = (defaultTokenField, dependenciesField, sentenceLengthField, src, traversal, dst).##

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
      new GraphTraversalQuery(defaultTokenField, dependenciesField, sentenceLengthField, rewrittenSrc, traversal, rewrittenDst)
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
      val numWordsPerDoc = reader.getNumericDocValues(sentenceLengthField)
      new GraphTraversalSpans(Array(srcSpans, dstSpans), traversal, graphPerDoc, numWordsPerDoc)
    }

  }

}

class GraphTraversalSpans(
    val subSpans: Array[OdinsonSpans],
    val traversal: GraphTraversal,
    val graphPerDoc: SortedDocValues,
    val numWordsPerDoc: NumericDocValues
) extends ConjunctionSpans {

  import Spans._

  val Array(srcSpans, dstSpans) = subSpans

  // resulting spans sorted by position
  private var pq: QueueByPosition = null

  private var topPositionOdinsonMatch: OdinsonMatch = null

  // dependency graph
  private var graph: DirectedGraph = null

  private var maxToken: Long = -1

  override def odinsonMatch: OdinsonMatch = topPositionOdinsonMatch

  def twoPhaseCurrentDocMatches(): Boolean = {
    oneExhaustedInCurrentDoc = false
    graph = UnsafeSerializer.bytesToGraph(graphPerDoc.get(docID()).bytes)
    maxToken = numWordsPerDoc.get(docID())
    pq = QueueByPosition.mkPositionQueue(matchPairs(graph, maxToken.toInt, traversal, srcSpans, dstSpans))
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

  private def matchPairs(
      graph: DirectedGraph,
      maxToken: Int,
      traversal: GraphTraversal,
      srcSpans: OdinsonSpans,
      dstSpans: OdinsonSpans
  ): Array[OdinsonMatch] = {
    val builder = new ArrayBuilder.ofRef[OdinsonMatch]
    val dstIndex = mkInvIndex(dstSpans.getAllMatches(), maxToken)
    val seen = HashSet.empty[OdinsonMatch]
    for {
      src <- srcSpans.getAllMatches()
      path <- traversal.traverseFrom(graph, src)
      dst <- dstIndex(path.end)
      if seen.add(dst)
    } builder += new GraphTraversalMatch(src, dst, path)
    builder.result()
  }

}
