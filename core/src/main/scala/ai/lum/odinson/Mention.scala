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
  def arguments: Map[String, Array[Mention]] = {
    odinsonMatch
      .namedCaptures
      .groupBy(_.name)
      .transform { (k,v) =>
        v.map { c =>
          Mention(
            c.capturedMatch,
            c.label,
            luceneDocId,
            luceneSegmentDocId,
            luceneSegmentDocBase,
            docId,
            sentenceId,
            // we mark the captures as matched by the same rule as the whole match
            foundBy)
        }
      }
  }

}

object Mention {
  val DefaultLabel = "**UNDEFINED**"
}
