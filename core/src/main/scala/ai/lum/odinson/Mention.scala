package ai.lum.odinson

class Mention(
  val odinsonMatch: OdinsonMatch,
  val label: Option[String],
  val luceneDocId: Int,
  val luceneSegmentDocId: Int,
  val luceneSegmentDocBase: Int,
  val idGetter: IdGetter,
  val foundBy: String,
  val arguments: Map[String, Array[Mention]] = Map.empty
) {

  def this(
    odinsonMatch: OdinsonMatch,
    label: Option[String],
    luceneDocId: Int,
    luceneSegmentDocId: Int,
    luceneSegmentDocBase: Int,
    idGetter: IdGetter,
    foundBy: String
  ) = {
    this(
      odinsonMatch,
      label,
      luceneDocId,
      luceneSegmentDocId,
      luceneSegmentDocBase,
      idGetter,
      foundBy,
      Mention.mkArguments(odinsonMatch, label, luceneDocId, luceneSegmentDocId, luceneSegmentDocBase, idGetter, foundBy)
    )
  }

  def docId = idGetter.getDocId

  def sentenceId = idGetter.getSentId

  def copy(
    odinsonMatch: OdinsonMatch = this.odinsonMatch,
    label: Option[String] = this.label,
    luceneDocId: Int = this.luceneDocId,
    luceneSegmentDocId: Int = this.luceneSegmentDocId,
    luceneSegmentDocBase: Int = this.luceneSegmentDocBase,
    idGetter: IdGetter = this.idGetter,
    foundBy: String = this.foundBy,
    arguments: Map[String, Array[Mention]] = this.arguments
  ): Mention = {
      new Mention(odinsonMatch, label, luceneDocId, luceneSegmentDocId, luceneSegmentDocBase, idGetter, foundBy, arguments)
  }

}

object Mention {
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
          new Mention(
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