package ai.lum.odinson

import ai.lum.odinson.DataGatherer.VerboseLevels
import ai.lum.odinson.lucene.index.OdinsonIndex
import ai.lum.odinson.lucene.search.OdinsonScoreDoc

// TODO: From Documents
// TODO: Deps
// TODO: Parent MetaData

class DataGatherer(index: OdinsonIndex) {

  val displayField = index.displayField

  val storedFields: Seq[String] = index.settings.storedFields

  def getStringForSpan(docID: Int, m: OdinsonMatch): String = {
    getTokensForSpan(docID, m).mkString(" ")
  }

  def getTokensForSpan(docID: Int, m: OdinsonMatch): Array[String] = {
    getTokensForSpan(docID, index.displayField, m.start, m.end)
  }

  def getTokensForSpan(docID: Int, m: OdinsonMatch, fieldName: String): Array[String] = {
    getTokensForSpan(docID, fieldName, m.start, m.end)
  }

  def getTokensForSpan(docID: Int, start: Int, end: Int): Array[String] = {
    getTokensForSpan(docID, index.displayField, start, end)
  }

  def getTokensForSpan(docID: Int, fieldName: String, start: Int, end: Int): Array[String] = {
    getTokens(docID, fieldName).slice(start, end)
  }

  def getTokens(scoreDoc: OdinsonScoreDoc): Array[String] = {
    getTokens(scoreDoc.doc, index.displayField)
  }

  def getTokens(scoreDoc: OdinsonScoreDoc, fieldName: String): Array[String] = {
    getTokens(scoreDoc.doc, fieldName)
  }

  def getTokens(docID: Int, fieldName: String): Array[String] = {
    index.getTokensFromMultipleFields(docID, Set(fieldName))(fieldName)
  }

  def getTokens(docID: Int, fieldNames: Set[String]): Map[String, Array[String]] = {
    index.getTokensFromMultipleFields(docID, fieldNames)
  }

  def fieldsToInclude(level: VerboseLevels.Verbosity = VerboseLevels.Display): Seq[String] = {
    // Determine which fields to include, given the specified level of verbosity
    // Note that since we already checked the validity of verbose and engine,
    // calling `get` here on the engine should not be a problem.
    level match {
      case VerboseLevels.Minimal => Seq.empty
      case VerboseLevels.Display => Seq(index.displayField)
      case VerboseLevels.All     => storedFields
    }
  }

  // ------------------------------
  //          Deprecated
  // ------------------------------

  @deprecated(
    message =
      "This method is deprecated, please use please use the `text()` method of the argument Mention",
    since = "0.3.2"
  )
  def getArgument(mention: Mention, name: String): String = {
    getStringForSpan(mention.luceneDocId, mention.arguments(name).head.odinsonMatch)
  }

  @deprecated(
    message = "This method is deprecated, please use Mention.mentionFields",
    since = "0.3.2"
  )
  def getTokensForSpan(m: Mention): Array[String] = {
    getTokensForSpan(m.luceneDocId, m.odinsonMatch, index.displayField)
  }

  @deprecated(
    message = "This method is deprecated, please use Mention.mentionFields",
    since = "0.3.2"
  )
  def getTokensForSpan(m: Mention, fieldName: String): Array[String] = {
    getTokensForSpan(m.luceneDocId, m.odinsonMatch, fieldName)
  }

}

object DataGatherer {

  def apply(index: OdinsonIndex): DataGatherer = new DataGatherer(index)

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
