package ai.lum.odinson

import ai.lum.odinson.DataGatherer.VerboseLevels
import ai.lum.odinson.utils.exceptions.OdinsonException
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable

class Mention(
  val odinsonMatch: OdinsonMatch,
  val label: Option[String],
  val luceneDocId: Int,
  val luceneSegmentDocId: Int,
  val luceneSegmentDocBase: Int,
  val idGetter: IdGetter,
  val foundBy: String,
  val arguments: Map[String, Array[Mention]] = Map.empty,
  dataGathererOpt: Option[DataGatherer]
) extends LazyLogging {

  def this(
    odinsonMatch: OdinsonMatch,
    label: Option[String],
    luceneDocId: Int,
    luceneSegmentDocId: Int,
    luceneSegmentDocBase: Int,
    idGetter: IdGetter,
    foundBy: String,
    dataGathererOpt: Option[DataGatherer]
  ) = {
    this(
      odinsonMatch,
      label,
      luceneDocId,
      luceneSegmentDocId,
      luceneSegmentDocBase,
      idGetter,
      foundBy,
      Mention.mkArguments(
        odinsonMatch,
        label,
        luceneDocId,
        luceneSegmentDocId,
        luceneSegmentDocBase,
        idGetter,
        foundBy,
        dataGathererOpt
      ),
      dataGathererOpt: Option[DataGatherer]
    )
  }

  def docId = idGetter.getDocId

  def sentenceId = idGetter.getSentId

  /** Token start offset, inclusive */
  def start: Int = odinsonMatch.start

  /** Token end offset, exclusive */
  def end: Int = odinsonMatch.end

  // Potentially cheaper than recreating mentions all the time
  // These will be modified when the Mention is populated
  private var _verbosity: VerboseLevels.Verbosity = VerboseLevels.Minimal
  private var _documentFields: Map[String, Array[String]] = Map.empty
  private var _mentionFields: Map[String, Array[String]] = Map.empty
  private var _text: Option[String] = None

  def documentFields: Map[String, Array[String]] = _documentFields
  def mentionFields: Map[String, Array[String]] = _mentionFields

  def text: String = _text match {
    case noTxt if noTxt.isEmpty => throw OdinsonException("Mention has not yet been populated")
    case txt                    => txt.get
  }

  def hasFieldsPopulated(level: VerboseLevels.Verbosity): Boolean = _verbosity >= level

  /** Populate the mention with the annotations stored in the index and the provided DataGatherer
    * @param level The level of population desired (all or some of the fields)
    * @param localDG The data gatherer to be used in populating the mentions
    * @return success
    */
  def populateFields(
    level: VerboseLevels.Verbosity = VerboseLevels.Display,
    localDG: Option[DataGatherer] = dataGathererOpt
  ): Boolean = {
    // Nothing was populated
    if (level == VerboseLevels.Minimal) return true

    // Don't repopulate if it's already there.
    if (hasFieldsPopulated(level)) return true

    // The user asked for a higher level of population, but there is no DataGatherer to do it.
    if (localDG.isEmpty) {
      throw OdinsonException("Unable to populate Mention fields, no DataGatherer provided.")
    }

    // The fields are determined by the verbosity level and what's available in the index
    val fieldsToInclude = localDG.get.fieldsToInclude(level).toSet

    val documentFields = new mutable.HashMap[String, Array[String]]()
    val mentionFields = new mutable.HashMap[String, Array[String]]()

    val documentTokens = localDG.get.getTokens(luceneDocId, fieldsToInclude)

    fieldsToInclude foreach { field =>
      // The tokens from this field for the whole Document (i.e., Sentence)
      val docTokens = documentTokens(field)
      documentFields(field) = docTokens
      // The slice of those fields that correspond to the Mention span
      val mentionTokens = docTokens.slice(start, end)
      mentionFields(field) = mentionTokens
    }

    // Store the populated fields
    _documentFields = documentFields.toMap
    _mentionFields = mentionFields.toMap
    _verbosity = level
    _text = Some(_mentionFields(localDG.get.displayField).mkString(" "))

    // Populate the arguments
    val argSuccesses = arguments.values.toSeq.flatten.map(_.populateFields(level, localDG))

    // Success!
    !argSuccesses.contains(false)
  }

  def manuallyPopulate(
    text: String,
    documentFields: Map[String, Array[String]],
    mentionFields: Map[String, Array[String]]
  ): Unit = {
    _documentFields = documentFields
    _mentionFields = mentionFields
    _text = Some(text)
  }

  def copy(
    odinsonMatch: OdinsonMatch = this.odinsonMatch,
    label: Option[String] = this.label,
    luceneDocId: Int = this.luceneDocId,
    luceneSegmentDocId: Int = this.luceneSegmentDocId,
    luceneSegmentDocBase: Int = this.luceneSegmentDocBase,
    idGetter: IdGetter = this.idGetter,
    foundBy: String = this.foundBy,
    arguments: Map[String, Array[Mention]] = this.arguments,
    dataGathererOpt: Option[DataGatherer] = this.dataGathererOpt
  ): Mention = {
    val out = new Mention(
      odinsonMatch,
      label,
      luceneDocId,
      luceneSegmentDocId,
      luceneSegmentDocBase,
      idGetter,
      foundBy,
      arguments,
      dataGathererOpt
    )
    // populate it at at the same level as the original, if the original was populated
    if (_verbosity > VerboseLevels.Minimal) {
      out.populateFields(_verbosity, dataGathererOpt)
    }
    out
  }

}

object Mention {

  /** A map from argument name to a sequence of mentions.
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
    dataGathererOpt: Option[DataGatherer]
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
            // todo: add foundBy info to state somehow
            foundBy,
            dataGathererOpt
          )
        }
      }
  }

}
