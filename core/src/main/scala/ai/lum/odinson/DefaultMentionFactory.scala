package ai.lum.odinson

import ai.lum.odinson.mention.IdGetter
import ai.lum.odinson.mention.Mention
import ai.lum.odinson.mention.MentionFactory

class DefaultMentionFactory extends MentionFactory {

  def newMention(
    odinsonMatch: OdinsonMatch,
    label: Option[String],
    luceneDocId: Int,
    luceneSegmentDocId: Int,
    luceneSegmentDocBase: Int,
    idGetter: IdGetter,
    foundBy: String,
    arguments: Map[String, Array[Mention]],
  ): Mention = {
    new Mention(odinsonMatch, label, luceneDocId, luceneSegmentDocId, luceneSegmentDocBase, idGetter, foundBy, arguments)
  }

}
