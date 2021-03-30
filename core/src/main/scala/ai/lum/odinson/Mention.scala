package ai.lum.odinson

import ai.lum.odinson
import ai.lum.odinson.DataGatherer.VerboseLevels
import ai.lum.odinson.serialization.JsonSerializer
import ai.lum.odinson.utils.exceptions.OdinsonException
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable.ArrayBuffer

class Mention(
  val odinsonMatch: OdinsonMatch,
  val label: Option[String],
  val luceneDocId: Int,
  val luceneSegmentDocId: Int,
  val luceneSegmentDocBase: Int,
  val idGetter: IdGetter,
  val foundBy: String,
  val arguments: Map[String, Array[Mention]] = Map.empty,
  @transient dataGathererOpt: Option[DataGatherer]
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
    case noTxt if noTxt.isEmpty => throw new OdinsonException("Mention has not yet been populated")
    case txt => txt.get
  }

  def hasFieldsPopulated(level: VerboseLevels.Verbosity): Boolean = _verbosity >= level

  private def getTokens(field: String): Array[String] = {
    if (!mentionFields.contains(field)) throw new OdinsonException(s"Unable to get field: [${field}], Mention was not populated")
    _mentionFields(field)
  }

  /**
    * Populate the mention with the annotations stored in the index and the provided DataGatherer
    * @param level The level of population desired (all or some of the fields)
    * @param localDG The data gatherer to be used in populating the mentions
    * @return success
    */
  def populateFields(level: VerboseLevels.Verbosity = VerboseLevels.Display, localDG: Option[DataGatherer] = dataGathererOpt): Boolean = {
    // Don't repopulate if it's already there.
    if (hasFieldsPopulated(level)) return true

    // Nothing was populated
    if (level == VerboseLevels.Minimal) {
      logger.warn("Calling `populateFields` with a verbosity of VerboseLevels.Minimal does not populate anything.")
      return false
    }

    // The user asked for a higher level of population, but there is no DataGatherer to do it.
    if (localDG.isEmpty) {
      throw new OdinsonException("Unable to populate Mention fields, no DataGatherer provided.")
    }

    // The fields are determined by the verbosity level and what's available in the index
    val fieldsToInclude = localDG.get.fieldsToInclude(level)
    val numFields = fieldsToInclude.length

    val documentFields = new Array[(String, Array[String])](numFields)
    val mentionFields = new Array[(String, Array[String])](numFields)

    fieldsToInclude.zipWithIndex foreach { case (field, idx) =>
      // The tokens from this field for the whole Document (i.e., Sentence)
      val docTokens = localDG.get.getTokens(luceneDocId, field)
      documentFields(idx) = (field, docTokens)
      // The slice of those fields that correspond to the Mention span
      val mentionTokens = docTokens.slice(start, end)
      mentionFields(idx) = (field, mentionTokens)
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
    // populate it at the same level as the original
    out.populateFields(_verbosity, dataGathererOpt)
    out
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
            // todo: FoundBy handler
            // todo: add foundBy info to state somehow
            foundBy,
            dataGathererOpt
          )
        }
      }
  }

}
