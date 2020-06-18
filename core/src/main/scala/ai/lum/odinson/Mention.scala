package ai.lum.odinson

import ai.lum.common.Interval

class Mention(
  val odinsonMatch: OdinsonMatch,
  val label: Option[String],
  val luceneDocId: Int,
  val luceneSegmentDocId: Int,
  val luceneSegmentDocBase: Int,
  val docId: String,
  val sentenceId: String,
  val foundBy: String
) {

  /** A map from argument name to a sequence of matches.
    *
    * The value of the map is a sequence because there are events
    * that can have several arguments with the same name.
    * For example, in the biodomain, Binding may have several themes.
    */
  def arguments(mentionFactory: MentionFactory /*= new DefaultMentionFactory*/): Map[String, Array[Mention]] = {
    odinsonMatch
      .namedCaptures
      .groupBy(_.name)
      .transform { (k,v) =>
        v.map { c =>
          mentionFactory.newMention(
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

  def copy(mentionFactory: MentionFactory /*= new DefaultMentionFactory*/,
    odinsonMatch: OdinsonMatch = this.odinsonMatch,
    label: Option[String] = this.label,
    luceneDocId: Int = this.luceneDocId,
    luceneSegmentDocId: Int = this.luceneSegmentDocId,
    luceneSegmentDocBase: Int = this.luceneSegmentDocBase,
    docId: String = this.docId,
    sentenceId: String = this.sentenceId,
    foundBy: String = this.foundBy): Mention = {
      mentionFactory.newMention(odinsonMatch, label, luceneDocId, luceneSegmentDocId, luceneSegmentDocBase, docId, sentenceId, foundBy)
  }
}
