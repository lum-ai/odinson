package utils

import ai.lum.common.StringUtils._

/** Used for columnar export */
case class OdinsonRow(
  odinsonQuery: String,
  parentQuery: Option[String],
  docId: String,
  sentenceIndex: Int,
  tokens: Seq[String],
  start: Int,
  end: Int,
  matchingSpan: String,
  metadata: DocumentMetadata
) {
  def toRow(delimiter: String = OdinsonRow.BASE_DELIMITER): String = {
    Seq(
      odinsonQuery,
      parentQuery.getOrElse(""),
      sentenceIndex,
      tokens.mkString(" "),
      start,
      end,
      matchingSpan,
      OdinsonRow.sterilizeAndQuote(docId),
      OdinsonRow.sterilize(metadata.title.getOrElse("")),
      metadata.authors.getOrElse(Nil).map(OdinsonRow.sterilize).mkString(OdinRow.ARRAY_DELIMITER),
      metadata.venue.getOrElse(""),
      metadata.year.getOrElse(""),
      metadata.doi.getOrElse(""),
      metadata.url.getOrElse(""),
    ).mkString(delimiter)
  }
}

object OdinsonRow {
  val ARRAY_DELIMITER = ";"
  val BASE_DELIMITER = "\t"
  def HEADER(delimiter: String = OdinsonRow.BASE_DELIMITER): String = Seq(
    "ODINSON_QUERY",
    "PARENT_QUERY",
    "SENTENCE_INDEX",
    "TOKENS",
    "START",
    "END",
    "MATCHING_SPAN",
    "DOC_ID",
    "TITLE",
    "AUTHORS",
    "VENUE",
    "YEAR",
    "DOI",
    "URL"
  ).mkString(delimiter)

  def sterilizeAndQuote(s: String) = s""""${sterilize(s)}""""
  def sterilize(s: String): String = s.trim.escapeCsv
}
