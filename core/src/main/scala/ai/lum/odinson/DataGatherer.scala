package ai.lum.odinson

import ai.lum.odinson.lucene.analysis.TokenStreamUtils
import ai.lum.odinson.lucene.search.OdinsonScoreDoc
import ai.lum.odinson.utils.IndexSettings
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.search.IndexSearcher

class DataGatherer(indexSearcher: IndexSearcher, val displayField: String, indexSettings: IndexSettings) {

  val analyzer = new WhitespaceAnalyzer()

  val storedFields = indexSettings.storedFields

  @deprecated(
    message =
      "This signature of getString is deprecated and will be removed in a future release. Use getStringForSpan(docID: Int, m: OdinsonMatch) instead.",
    since = "https://github.com/lum-ai/odinson/commit/89ceb72095d603cf61d27decc7c42c5eea50c87a"
  )
  def getString(docID: Int, m: OdinsonMatch): String = {
    getTokens(docID, m).mkString(" ")
  }

  def getStringForSpan(docID: Int, m: OdinsonMatch): String = {
    getTokensForSpan(docID, m).mkString(" ")
  }

  def getArgument(mention: Mention, name: String): String = {
    getStringForSpan(mention.luceneDocId, mention.arguments(name).head.odinsonMatch)
  }

  @deprecated(
    message =
      "This signature of getTokens is deprecated and will be removed in a future release. Use getTokensForSpan(m: Mention) instead.",
    since = "https://github.com/lum-ai/odinson/commit/89ceb72095d603cf61d27decc7c42c5eea50c87a"
  )
  def getTokens(m: Mention): Array[String] = {
    getTokens(m.luceneDocId, m.odinsonMatch)
  }

  def getTokensForSpan(m: Mention): Array[String] = {
    getTokensForSpan(m.luceneDocId, m.odinsonMatch, displayField)
  }

  def getTokensForSpan(m: Mention, fieldName: String): Array[String] = {
    getTokensForSpan(m.luceneDocId, m.odinsonMatch, fieldName)
  }

  def getTokensForSpan(docID: Int, m: OdinsonMatch): Array[String] = {
    getTokensForSpan(docID, displayField, m.start, m.end)
  }

  def getTokensForSpan(docID: Int, m: OdinsonMatch, fieldName: String): Array[String] = {
    getTokensForSpan(docID, fieldName, m.start, m.end)
  }

  def getTokensForSpan(docID: Int, start: Int, end: Int): Array[String] = {
    getTokensForSpan(docID, displayField, start, end)
  }

  def getTokensForSpan(docID: Int, fieldName: String, start: Int, end: Int): Array[String] = {
    getTokens(docID, fieldName).slice(start, end)
  }

  @deprecated(
    message =
      "This signature of getTokens is deprecated and will be removed in a future release. Use getTokensForSpan(docID: Int, m: OdinsonMatch) instead.",
    since = "https://github.com/lum-ai/odinson/commit/89ceb72095d603cf61d27decc7c42c5eea50c87a"
  )
  def getTokens(docID: Int, m: OdinsonMatch): Array[String] = {
    getTokens(docID, displayField).slice(m.start, m.end)
  }

  def getTokens(scoreDoc: OdinsonScoreDoc): Array[String] = {
    getTokens(scoreDoc.doc, displayField)
  }

  def getTokens(scoreDoc: OdinsonScoreDoc, fieldName: String): Array[String] = {
    getTokens(scoreDoc.doc, fieldName)
  }

  def getTokens(docID: Int, fieldName: String): Array[String] = {
    TokenStreamUtils.getTokens(docID, fieldName, indexSearcher, analyzer)
  }

}

object DataGatherer {

}
