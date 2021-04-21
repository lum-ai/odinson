package ai.lum.odinson.lucene.analysis

import ai.lum.odinson.utils.exceptions.OdinsonException

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.document.Document
import org.apache.lucene.index.Fields
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.highlight.TokenSources


object TokenStreamUtils {

  def getDoc(docID: Int, fieldNames: Set[String], indexSearcher: IndexSearcher): Document = {
    indexSearcher.doc(docID, fieldNames.asJava)
  }

  def getTokensFromMultipleFields(
    docID: Int,
    fieldNames: Set[String],
    indexSearcher: IndexSearcher,
    analyzer: Analyzer
  ): Map[String, Array[String]] = {
    val doc = getDoc(docID, fieldNames, indexSearcher)
    val tvs = indexSearcher.getIndexReader().getTermVectors(docID)
    fieldNames
      .map(field => (field, getTokens(doc, tvs, field, analyzer)))
      .toMap
  }

  def getTokens(
    doc: Document,
    tvs: Fields,
    fieldName: String,
    analyzer: Analyzer
  ): Array[String] = {
    val field = doc.getField(fieldName)
    if (field == null) throw new OdinsonException(
      s"Attempted to getTokens from field that was not stored: $fieldName"
    )
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
