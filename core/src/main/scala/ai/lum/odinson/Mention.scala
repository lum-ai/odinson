package ai.lum.odinson

class Mention(
  val odinsonMatch: OdinsonMatch,
  val label: Option[String],
  val luceneDocId: Int,
  val luceneSegmentDocId: Int,
  val luceneSegmentDocBase: Int,
  val docId: String,
  val sentenceId: String,
  val foundBy: String,
  val arguments: Map[String, Array[Mention]] = Map.empty
) {

  def copy(
    mentionFactory: MentionFactory /*= new DefaultMentionFactory*/,
    odinsonMatch: OdinsonMatch = this.odinsonMatch,
    label: Option[String] = this.label,
    luceneDocId: Int = this.luceneDocId,
    luceneSegmentDocId: Int = this.luceneSegmentDocId,
    luceneSegmentDocBase: Int = this.luceneSegmentDocBase,
    docId: String = this.docId,
    sentenceId: String = this.sentenceId,
    foundBy: String = this.foundBy,
    arguments: Map[String, Array[Mention]] = this.arguments
  ): Mention = {
      mentionFactory.newMention(odinsonMatch, label, luceneDocId, luceneSegmentDocId, luceneSegmentDocBase, docId, sentenceId, foundBy, arguments)
  }
}
