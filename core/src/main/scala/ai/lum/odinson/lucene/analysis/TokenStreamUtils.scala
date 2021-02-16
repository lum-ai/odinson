package ai.lum.odinson.lucene.analysis

import ai.lum.odinson.utils.exceptions.OdinsonException

import scala.collection.mutable.ArrayBuffer
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.highlight.TokenSources

object TokenStreamUtils {

  def getTokens(
    docID: Int,
    fieldName: String,
    indexSearcher: IndexSearcher,
    analyzer: Analyzer
  ): Array[String] = {
    val doc = indexSearcher.doc(docID)
    val tvs = indexSearcher.getIndexReader().getTermVectors(docID)
    val field = doc.getField(fieldName)
    if (field == null) throw new OdinsonException(s"Attempted to getTokens from field that was not stored: $fieldName")
    val text = field.stringValue
    val ts = TokenSources.getTokenStream(fieldName, tvs, text, analyzer, -1)
    val tokens = getTokens(ts)
    tokens
  }

  def getTokens(ts: TokenStream): Array[String] = {
    ts.reset()
    val terms = new ArrayBuffer[String]

    while (ts.incrementToken()) {
      val charTermAttribute = ts.addAttribute(classOf[CharTermAttribute])
      val term = charTermAttribute.toString
      terms += term
    }

    ts.end()
    ts.close()

    terms.toArray
  }

}
