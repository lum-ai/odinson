package ai.lum.odinson.lucene.search

import java.util.{ Map => JMap, Set => JSet }
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson.lucene.search.spans._

class AllNGramsQuery(
  val defaultTokenField: String,
  val sentenceLengthField: String,
  val n: Int
) extends OdinsonQuery { self =>

  override def hashCode: Int = (defaultTokenField, sentenceLengthField, n).##

  def toString(field: String): String = s"AllNGramsQuery($n)"

  def getField(): String = defaultTokenField

  override def createWeight(searcher: IndexSearcher, needsScores: Boolean): OdinsonWeight = {
    new AllNGramsWeight(searcher, null)
  }

  class AllNGramsWeight(
    searcher: IndexSearcher,
    termContexts: JMap[Term, TermContext]
  ) extends OdinsonWeight(self, searcher, termContexts) {

    def extractTerms(terms: JSet[Term]): Unit = {
      terms.addAll(termContexts.keySet())
    }

    def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = ()

    def getSpans(
      context: LeafReaderContext,
      requiredPostings: SpanWeight.Postings
    ): OdinsonSpans = {
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
) extends OdinsonSpans {

  import DocIdSetIterator._
  import Spans._

  private var matchStart: Int = -1

  private var matchEnd: Int = -1

  private var currentDoc: Int = -1

  private val maxDoc: Int = reader.maxDoc()

  private var maxToken: Long = -1

  def cost(): Long = maxDoc.toLong

  def docID(): Int = currentDoc

  def nextDoc(): Int = advance(currentDoc + 1)

  def advance(target: Int): Int = {
    @annotation.tailrec
    def getNextDocId(nextDocId: Int): Int = {
      if (nextDocId >= maxDoc) {
        NO_MORE_DOCS
      } else {
        maxToken = numWordsPerDoc.get(nextDocId)
        // Check that the sentence is big enough.
        // If n == 0 we should skip docs with maxToken 0 anyway
        // because those are metadata documents, not sentences.
        if (maxToken < n || maxToken == 0) {
          getNextDocId(nextDocId + 1)
        } else {
          nextDocId
        }
      }
    }
    currentDoc = getNextDocId(target)
    if (currentDoc != NO_MORE_DOCS) {
      // reset positions in document
      matchStart = -1
      matchEnd = -1
    }
    currentDoc
  }

  def nextStartPosition(): Int = {
    matchStart += 1
    matchEnd = matchStart + n
    if (matchEnd > maxToken) {
      matchStart = NO_MORE_POSITIONS
      matchEnd = NO_MORE_POSITIONS
    }
    matchStart
  }

  def startPosition(): Int = matchStart

  def endPosition(): Int = matchEnd

  def collect(collector: SpanCollector): Unit = ???

  def positionsCost(): Float = 1

}
