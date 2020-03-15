package ai.lum.odinson

case class Mention(
  odinsonMatch: OdinsonMatch,
  label: String,
  luceneDocId: Int,
  luceneSegmentDocId: Int,
  luceneSegmentDocBase: Int,
  docId: String,
  sentenceId: String,
  foundBy: String,
)

object Mention {
  val DefaultLabel = "**UNDEFINED**"
}
