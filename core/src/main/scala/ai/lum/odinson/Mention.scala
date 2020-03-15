package ai.lum.odinson

import ai.lum.common.Interval

case class Mention(
  odinsonMatch: OdinsonMatch,
  label: String,
  luceneDocId: Int,
  luceneSegmentDocId: Int,
  luceneSegmentDocBase: Int,
  docId: String,
  sentenceId: String,
  foundBy: String,
) {

  /** A map from argument name to a sequence of matches.
    *
    * The value of the map is a sequence because there are events
    * that can have several arguments with the same name.
    * For example, in the biodomain, Binding may have several themes.
    */
  def arguments: Map[String, Array[OdinsonMatch]] = {
    odinsonMatch.namedCaptures
      .groupBy(_.name)
      .transform((k,v) => v.map(_.capturedMatch))
  }

}

object Mention {
  val DefaultLabel = "**UNDEFINED**"
}
