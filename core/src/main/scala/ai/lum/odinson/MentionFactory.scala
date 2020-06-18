package ai.lum.odinson

trait MentionFactory {

  def newMention(odinsonMatch: OdinsonMatch,
    label: Option[String],
    luceneDocId: Int,
    luceneSegmentDocId: Int,
    luceneSegmentDocBase: Int,
    docId: String,
    sentenceId: String,
    foundBy: String): Mention
}
