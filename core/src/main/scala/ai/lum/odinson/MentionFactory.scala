package ai.lum.odinson

trait IdGetter {
  def getDocId: String
  def getSentId: String
}

trait MentionFactory {

  // If you are implementing a custom MentionFactory, this is the primary method
  // you need to implement.
  def newMention(
    odinsonMatch: OdinsonMatch,
    label: Option[String],
    luceneDocId: Int,
    luceneSegmentDocId: Int,
    luceneSegmentDocBase: Int,
    idGetter: IdGetter,
    foundBy: String,
    arguments: Map[String, Array[Mention]],
  ): Mention

  def newMention(
    odinsonMatch: OdinsonMatch,
    label: Option[String],
    luceneDocId: Int,
    luceneSegmentDocId: Int,
    luceneSegmentDocBase: Int,
    idGetter: IdGetter,
    foundBy: String
  ): Mention = {
    val arguments = mkArguments(odinsonMatch, label, luceneDocId, luceneSegmentDocId, luceneSegmentDocBase, idGetter, foundBy)
    newMention(odinsonMatch, label, luceneDocId, luceneSegmentDocId, luceneSegmentDocBase, idGetter, foundBy, arguments)
  }

  /** A map from argument name to a sequence of matches.
    *
    * The value of the map is a sequence because there are events
    * that can have several arguments with the same name.
    * For example, in the biodomain, Binding may have several themes.
    */
  def mkArguments(
    odinsonMatch: OdinsonMatch,
    label: Option[String],
    luceneDocId: Int,
    luceneSegmentDocId: Int,
    luceneSegmentDocBase: Int,
    idGetter: IdGetter,
    foundBy: String,
  ): Map[String, Array[Mention]] = {
    odinsonMatch
      .namedCaptures
      // get all the matches for each name
      .groupBy(_.name)
      .transform { (name, captures) =>
        // Make a mention from each match in the named capture
        captures.map { capture =>
          newMention(
            capture.capturedMatch,
            capture.label,
            luceneDocId,
            luceneSegmentDocId,
            luceneSegmentDocBase,
            idGetter,
            // we mark the captures as matched by the same rule as the whole match
            // todo: FoundBy handler
            // todo: add foundBy info to state somehow
            foundBy)
        }
      }
  }

}
