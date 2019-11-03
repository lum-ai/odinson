package ai.lum.odinson.lucene.search

import java.util.{ Map => JMap, Set => JSet }
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson.lucene.search.spans._

class DocEndQuery(
    val defaultTokenField: String,
    val sentenceLengthField: String
) extends OdinsonQuery { self =>

  override def hashCode: Int = (defaultTokenField, sentenceLengthField).##

  def toString(field: String): String = "DocEndQuery"

  def getField(): String = defaultTokenField

  override def createWeight(searcher: IndexSearcher, needsScores: Boolean): OdinsonWeight = {
    new DocEndWeight(searcher, null)
  }

  class DocEndWeight(
      searcher: IndexSearcher,
      termContexts: JMap[Term, TermContext]
  ) extends OdinsonWeight(self, searcher, termContexts) {

    def extractTerms(terms: JSet[Term]): Unit = {
      terms.addAll(termContexts.keySet)
    }

    def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = ()

    def getSpans(context: LeafReaderContext, requiredPostings: SpanWeight.Postings): OdinsonSpans = {
      val reader = context.reader
      val numWordsPerDoc = reader.getNumericDocValues(sentenceLengthField)
      new DocEndSpans(reader, numWordsPerDoc)
    }

  }

}

class DocEndSpans(
    val reader: IndexReader,
    val numWordsPerDoc: NumericDocValues
) extends OdinsonSpans {

  import DocIdSetIterator._
  import Spans._

  private var matchStart: Int = -1

  private var currentDoc: Int = -1

  private val maxDoc: Int = reader.maxDoc

  private var maxToken: Long = -1

  def cost(): Long = maxDoc.toLong

  def docID(): Int = currentDoc

  def nextDoc(): Int = advance(currentDoc + 1)

  def advance(target: Int): Int = {
    if (target >= maxDoc) {
      currentDoc = NO_MORE_DOCS
    } else {
      currentDoc = target
      maxToken = numWordsPerDoc.get(currentDoc)
      matchStart = -1
    }
    currentDoc
  }

  def nextStartPosition(): Int = {
    if (matchStart == -1) {
      matchStart = maxToken.toInt // FIXME
    } else if (matchStart == maxToken) {
      matchStart = NO_MORE_POSITIONS
    } else if (matchStart == NO_MORE_POSITIONS) {
      // nothing
    } else {
      sys.error("DocEndSpans.nextStartPosition")
    }
    matchStart
  }

  def startPosition(): Int = matchStart

  def endPosition(): Int = matchStart

  def collect(collector: SpanCollector): Unit = ???

  def positionsCost(): Float = 1

}
