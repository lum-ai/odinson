package ai.lum.odinson

import ai.lum.odinson.DataGatherer.VerboseLevels
import ai.lum.odinson.lucene.analysis.TokenStreamUtils
import ai.lum.odinson.lucene.search.{OdinsonIndexSearcher, OdinsonScoreDoc}
import ai.lum.odinson.utils.IndexSettings
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.index.IndexReader
import org.apache.lucene.store.Directory

// TODO: From Documents
// TODO: Deps
// TODO: Parent MetaData

class DataGatherer(
  val indexReader: IndexReader,
  val displayField: String,
  indexSettings: IndexSettings
) {

  val analyzer = new WhitespaceAnalyzer()

  val storedFields = indexSettings.storedFields

  def getStringForSpan(docID: Int, m: OdinsonMatch): String = {
    getTokensForSpan(docID, m).mkString(" ")
  }

  def getArgument(mention: Mention, name: String): String = {
    getStringForSpan(mention.luceneDocId, mention.arguments(name).head.odinsonMatch)
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

  def getTokens(scoreDoc: OdinsonScoreDoc): Array[String] = {
    getTokens(scoreDoc.doc, displayField)
  }

  def getTokens(scoreDoc: OdinsonScoreDoc, fieldName: String): Array[String] = {
    getTokens(scoreDoc.doc, fieldName)
  }

  // TODO: deprecate this, and then remove -- this change is to preserve functionality
  //   in the meantime
  def getTokens(docID: Int, fieldName: String): Array[String] = {
    TokenStreamUtils
      .getTokensFromMultipleFields(docID, Set(fieldName), indexReader, analyzer)(fieldName)
  }

  def getTokens(docID: Int, fieldNames: Set[String]): Map[String, Array[String]] = {
    TokenStreamUtils.getTokensFromMultipleFields(docID, fieldNames, indexReader, analyzer)
  }

  def fieldsToInclude(level: VerboseLevels.Verbosity = VerboseLevels.Display): Seq[String] = {
    // Determine which fields to include, given the specified level of verbosity
    // Note that since we already checked the validity of verbose and engine,
    // calling `get` here on the engine should not be a problem.
    level match {
      case VerboseLevels.Minimal => Seq.empty
      case VerboseLevels.Display => Seq(displayField)
      case VerboseLevels.All     => storedFields
    }
  }

}

object DataGatherer {

  def apply(
    indexReader: IndexReader,
    displayField: String,
    indexDir: Directory
  ): DataGatherer = {
    new DataGatherer(indexReader, displayField, IndexSettings.fromDirectory(indexDir))
  }

  // Enum to handle the supported levels of verbosity of Mentions.
  //  - Minimal:  No additional text included
  //  - Display:  Display field included
  //  - All:      All stored fields included
  object VerboseLevels extends Enumeration {
    type Verbosity = Value

    // Default ordering is in order provided
    val Minimal, Display, All = Value

  }

}
