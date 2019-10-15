package ai.lum.odinson.lucene.search

import java.util.{ Map => JMap, Set => JSet }
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson.lucene.search.spans._

class DocStartQuery(val field: String) extends OdinsonQuery { self =>

  override def hashCode: Int = (field).##

  def toString(field: String): String = "DocStartQuery"

  def getField(): String = field

  override def createWeight(searcher: IndexSearcher, needsScores: Boolean): OdinsonWeight = {
    new DocStartWeight(searcher, null)
  }

  class DocStartWeight(
      searcher: IndexSearcher,
      termContexts: JMap[Term, TermContext]
  ) extends OdinsonWeight(self, searcher, termContexts) {

    def extractTerms(terms: JSet[Term]): Unit = {
      terms.addAll(termContexts.keySet)
    }

    def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = ()

    def getSpans(context: LeafReaderContext, requiredPostings: SpanWeight.Postings): OdinsonSpans = {
      new DocStartSpans(context.reader)
    }

  }

}

class DocStartSpans(val reader: IndexReader) extends OdinsonSpans {

  import DocIdSetIterator._
  import Spans._

  private var matchStart: Int = -1

  private var currentDoc: Int = -1

  private val maxDoc: Int = reader.maxDoc

  def cost(): Long = maxDoc.toLong

  def docID(): Int = currentDoc

  def nextDoc(): Int = advance(currentDoc + 1)

  def advance(target: Int): Int = {
    if (target >= maxDoc) {
      currentDoc = NO_MORE_DOCS
    } else {
      currentDoc = target
      matchStart = -1
    }
    currentDoc
  }

  def nextStartPosition(): Int = {
    matchStart += 1
    if (matchStart > 0) {
      matchStart = NO_MORE_POSITIONS
    }
    matchStart
  }

  def startPosition(): Int = matchStart

  def endPosition(): Int = matchStart

  def collect(collector: SpanCollector): Unit = ???

  def positionsCost(): Float = 1

}
