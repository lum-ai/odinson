package ai.lum.odinson

class DefaultMentionFactory extends MentionFactory {

  def newMention(odinsonMatch: OdinsonMatch,
    label: Option[String],
    luceneDocId: Int,
    luceneSegmentDocId: Int,
    luceneSegmentDocBase: Int,
    docId: String,
    sentenceId: String,
    foundBy: String): Mention = new Mention(odinsonMatch, label, luceneDocId, luceneSegmentDocId, luceneSegmentDocBase, docId, sentenceId, foundBy)
}
