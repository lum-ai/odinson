package ai.lum.odinson.lucene.search

import java.util.{ Map => JMap, Set => JSet }
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson.lucene.search.spans._
import ai.lum.odinson.state.State

class StateQuery(
  val field: String,
  val label: String
) extends OdinsonQuery { self =>

  var stateOpt: Option[State] = None

  override def setState(stateOpt: Option[State]): Unit = this.stateOpt = stateOpt

  override def hashCode: Int = (field, label).##

  def toString(field: String): String = "StateQuery"

  def getField(): String = field

  override def createWeight(
    searcher: IndexSearcher,
    needsScores: Boolean
  ): OdinsonWeight = {
    new StateWeight(searcher, null, label, stateOpt)
  }

  class StateWeight(
    searcher: IndexSearcher,
    termContexts: JMap[Term, TermContext],
    label: String,
    stateOpt: Option[State]
  ) extends OdinsonWeight(self, searcher, termContexts) {

    def extractTerms(terms: JSet[Term]): Unit = {
      terms.addAll(termContexts.keySet)
    }

    def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = ()

    def getSpans(
      context: LeafReaderContext,
      requiredPostings: SpanWeight.Postings
    ): OdinsonSpans = {
      new StateSpans(label, context.docBase, stateOpt)
    }

  }

  class StateSpans(
    val label: String,
    val docBase: Int,
    val stateOpt: Option[State]
  ) extends OdinsonSpans {

    import DocIdSetIterator._
    import Spans._

    // retrieve all segment-specific doc-ids corresponding to
    // the documents that contain a mention with the specified label
    private val docIds: Array[Int] = stateOpt.map(_.getDocIds(docBase, label)).getOrElse(Array.empty)
    private var currentDocIndex: Int = -1
    private var currentDoc: Int = -1

    private var startMatches: Array[Int] = null
    private var endMatches: Array[Int] = null
    private var currentMatchIndex: Int = -1
    private var matchStart: Int = -1
    private var matchEnd: Int = -1

    def cost(): Long = docIds.length.toLong

    def docID(): Int = currentDoc

    def nextDoc(): Int = advance(currentDoc + 1)

    def advance(target: Int): Int = {
      val from = if (currentDocIndex < 0) 0 else currentDocIndex
      val idx = docIds.indexWhere(_ >= target, from)
      if (idx == -1) {
        currentDoc = NO_MORE_DOCS
      } else {
        // advance to target doc
        currentDocIndex = idx
        currentDoc = docIds(currentDocIndex)
        matchStart = -1
        // retrieve mentions
        val (starts, ends) = stateOpt.get.getMatches(docBase, currentDoc, label).unzip
        startMatches = starts
        endMatches = ends
        currentMatchIndex = -1
        matchStart = -1
        matchEnd = -1
      }
      currentDoc
    }

    def nextStartPosition(): Int = {
      if (currentMatchIndex + 1 < startMatches.length) {
        currentMatchIndex += 1
        matchStart = startMatches(currentMatchIndex)
        matchEnd = endMatches(currentMatchIndex)
      } else {
        matchStart = NO_MORE_POSITIONS
        matchEnd = NO_MORE_POSITIONS
      }
      matchStart
    }

    def startPosition(): Int = matchStart

    def endPosition(): Int = matchEnd

    def collect(collector: SpanCollector): Unit = ???

    def positionsCost(): Float = 1 // because why not

  }

}
