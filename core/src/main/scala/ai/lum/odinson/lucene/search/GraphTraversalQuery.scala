package ai.lum.odinson.lucene.search

import java.util.{Map => JMap, Set => JSet}

import scala.collection.mutable.{ArrayBuffer, ArrayBuilder, HashMap}
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson.digraph._
import ai.lum.odinson._
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search.spans._
import ai.lum.odinson.lucene.util._
import ai.lum.odinson.serialization.UnsafeSerializer
import ai.lum.odinson.state.State

/** Traverses the graph from `src` to `dst` following the traversal pattern.
 *  Returns `dst` if there is a match.
 */
class GraphTraversalQuery(
    val defaultTokenField: String,
    val dependenciesField: String,
    val sentenceLengthField: String,
    val src: OdinsonQuery,
    val fullTraversal: FullTraversalQuery,
) extends OdinsonQuery { self =>

  // TODO GraphTraversal.hashCode
  override def hashCode: Int = (defaultTokenField, dependenciesField, sentenceLengthField, src, fullTraversal).##

  override def setState(stateOpt: Option[State]): Unit = {
    src.setState(stateOpt)
    fullTraversal.setState(stateOpt)
  }

  def toString(field: String): String = {
    val s = src.toString(field)
    val t = fullTraversal.toString(field)
    s"GraphTraversal($s, $t)"
  }

  def getField(): String = defaultTokenField

  override def rewrite(reader: IndexReader): Query = {
    val rewrittenSrc = src.rewrite(reader).asInstanceOf[OdinsonQuery]
    val rewrittenTraversal = fullTraversal.rewrite(reader)
    if (src != rewrittenSrc || fullTraversal != rewrittenTraversal) {
      new GraphTraversalQuery(defaultTokenField, dependenciesField, sentenceLengthField, rewrittenSrc, rewrittenTraversal)
    } else {
      super.rewrite(reader)
    }
  }

  override def createWeight(searcher: IndexSearcher, needsScores: Boolean): OdinsonWeight = {
    val srcWeight = src.createWeight(searcher, needsScores).asInstanceOf[OdinsonWeight]
    val traversalWeight = fullTraversal.createWeight(searcher, needsScores)
    val subWeights = srcWeight :: traversalWeight.subWeights
    val terms = if (needsScores) OdinsonQuery.getTermContexts(subWeights: _*) else null
    new GraphTraversalWeight(srcWeight, traversalWeight, searcher, terms)
  }

  class GraphTraversalWeight(
      val srcWeight: OdinsonWeight,
      val fullTraversal: FullTraversalWeight,
      searcher: IndexSearcher,
      terms: JMap[Term, TermContext]
  ) extends OdinsonWeight(self, searcher, terms) {

    def extractTerms(terms: JSet[Term]): Unit = {
      srcWeight.extractTerms(terms)
      fullTraversal.extractTerms(terms)
    }

    def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = {
      srcWeight.extractTermContexts(contexts)
      fullTraversal.extractTermContexts(contexts)
    }

    def getSpans(context: LeafReaderContext, requiredPostings: SpanWeight.Postings): OdinsonSpans = {
      val reader = context.reader
      val srcSpans = srcWeight.getSpans(context, requiredPostings)
      val traversalSpans = fullTraversal.getSpans(context, requiredPostings)
      if (srcSpans == null || traversalSpans == null) return null
      val graphPerDoc = reader.getSortedDocValues(dependenciesField)
      val numWordsPerDoc = reader.getNumericDocValues(sentenceLengthField)
      val subSpans = srcSpans :: traversalSpans.subSpans
      new GraphTraversalSpans(subSpans.toArray, srcSpans, traversalSpans, graphPerDoc, numWordsPerDoc)
    }

  }

}

class GraphTraversalSpans(
    val subSpans: Array[OdinsonSpans],
    val srcSpans: OdinsonSpans,
    val fullTraversal: FullTraversalSpans,
    val graphPerDoc: SortedDocValues,
    val numWordsPerDoc: NumericDocValues
) extends ConjunctionSpans {

  import Spans._

  // resulting spans sorted by position
  private var pq: QueueByPosition = null

  private var topPositionOdinsonMatch: OdinsonMatch = null

  // dependency graph
  private var graph: DirectedGraph = null

  private var maxToken: Long = -1

  override def odinsonMatch: OdinsonMatch = topPositionOdinsonMatch

  def twoPhaseCurrentDocMatches(): Boolean = {
    oneExhaustedInCurrentDoc = false
    fullTraversal.advanceToDoc(docID())
    graph = UnsafeSerializer.bytesToGraph(graphPerDoc.get(docID()).bytes)
    maxToken = numWordsPerDoc.get(docID())
    pq = QueueByPosition.mkPositionQueue(fullTraversal.matchFullTraversal(graph, maxToken.toInt, srcSpans.getAllMatches()))
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

  override def toMatchDoc(): Int = {
    val doc = super.toMatchDoc()
    fullTraversal.advanceToDoc(doc)
    doc
  }

}
