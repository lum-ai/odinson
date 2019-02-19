package ai.lum.odinson.lucene

import java.util.{ Map => JMap, Set => JSet }
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson.lucene.search._

class AllNGramsQuery(
    val defaultTokenField: String,
    val sentenceLengthField: String,
    val n: Int
) extends OdinQuery { self =>

  override def hashCode: Int = mkHash(defaultTokenField, n)

  def toString(field: String): String = s"AllNGramsQuery($n)"

  def getField(): String = defaultTokenField

  override def createWeight(searcher: IndexSearcher, needsScores: Boolean): OdinWeight = {
    new AllNGramsWeight(searcher, null)
  }

  class AllNGramsWeight(
      searcher: IndexSearcher,
      termContexts: JMap[Term, TermContext]
  ) extends OdinWeight(self, searcher, termContexts) {

    def extractTerms(terms: JSet[Term]): Unit = {
      terms.addAll(termContexts.keySet())
    }

    def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = ()

    def getSpans(context: LeafReaderContext, requiredPostings: SpanWeight.Postings): OdinSpans = {
      val reader = context.reader()
      val numWordsPerDoc = reader.getNumericDocValues(sentenceLengthField)
      new AllNGramsSpans(reader, numWordsPerDoc, n)
    }

  }

}

class AllNGramsSpans(
    val reader: IndexReader,
    val numWordsPerDoc: NumericDocValues,
    val n: Int
) extends OdinSpans {

  import DocIdSetIterator._
  import Spans._

  private var matchStart: Int = -1

  private var currentDoc: Int = -1

  private val maxDoc: Int = reader.maxDoc()

  private var maxToken: Long = -1

  def cost(): Long = maxDoc.toLong

  def docID(): Int = currentDoc

  def nextDoc(): Int = advance(currentDoc + 1)

  def advance(target: Int): Int = {
    if (target >= maxDoc) {
      currentDoc = NO_MORE_DOCS
    } else {
      currentDoc = target
      // get number of tokens in sentence
      maxToken = numWordsPerDoc.get(currentDoc)
      // reset positions in document
      matchStart = -1
    }
    currentDoc
  }

  def nextStartPosition(): Int = {
    matchStart += 1
    if (matchStart + n > maxToken) {
      matchStart = NO_MORE_POSITIONS
    }
    matchStart
  }

  def startPosition(): Int = matchStart

  def endPosition(): Int = matchStart + n

  def collect(collector: SpanCollector): Unit = ???

  def positionsCost(): Float = 1

}
