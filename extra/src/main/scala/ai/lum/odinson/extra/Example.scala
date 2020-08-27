package ai.lum.odinson.extra

import java.io.File

import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.odinson.{EventMatch, ExtractorEngine, NamedCapture, OdinsonMatch}
import com.typesafe.scalalogging.LazyLogging
import upickle.default._

// Wrapper class for mention info to facilitate example exporting, not
// intended to be comprehensive.
case class MentionInfo(
  luceneDocId: Int,
  docId: String,
  sentenceId: String,
  sentenceText: String,
  foundBy: String,
  args: Seq[ArgInfo],
)
{
  def toJson: String = write(this)
}
object MentionInfo {implicit val rw: ReadWriter[MentionInfo] = macroRW}

// Wrapper for named captures (i.e., arguments) for use in the example,
// again not intended to be comprehensive.
case class ArgInfo(role: String, tokens: Seq[String])
{
  def toJson: String = write(this)
}
object ArgInfo {implicit val rw: ReadWriter[ArgInfo] = macroRW}

object Example extends App with LazyLogging{

  // Specify paths and settings in the local config file
  val config = ConfigFactory.load()
  val outputFile: File = config[File]("odinson.extra.outputFile")
  val rulesFile: File = config[File]("odinson.extra.rulesFile")

  // Initialize the extractor engine, using the index specified in the config
  val extractorEngine = ExtractorEngine.fromConfig()
  val extractors = extractorEngine.ruleReader.compileRuleFile(rulesFile)

  // Extract Mentions
  val mentions = extractorEngine.extractMentions(extractors)

  // Export Mentions
  val jsonMentions = for {
    mention <- mentions
    // Get the OdinsonMatch
    m = mention.odinsonMatch
    // Get the source for the extraction (document and sentence index)
    luceneDocID = mention.luceneDocId
    sentence = extractorEngine.getTokens(luceneDocID, extractorEngine.displayField).mkString(" ")
    // Get the name of the rule that found the extraction
    foundBy = mention.foundBy
    // Get the results of the rule, with the trigger if there is one
    namedCaptures = m.namedCaptures ++ triggerNamedCaptureOpt(m).toSeq
    args = mkArgs(luceneDocID, namedCaptures)
  } yield {
    // Combine the info into a wrapper class and convert to json
    MentionInfo(luceneDocID, mention.idGetter.getDocId, mention.idGetter.getSentId, sentence, foundBy, args).toJson
  }

  // Export as json lines format (i.e., one json per mention, one per line)
  outputFile.writeString(jsonMentions.mkString("\n"))


  private def triggerNamedCaptureOpt(m: OdinsonMatch): Option[NamedCapture] = {
    m match {
      case em: EventMatch => Some(NamedCapture("trigger", None, em.trigger))
      case _ => None
    }
  }

  private def mkArgs(luceneDocID: Int, namedCaptures: Array[NamedCapture]): Seq[ArgInfo] = {
    for {
      nc <- namedCaptures
      argName = nc.name
      capturedMatch = nc.capturedMatch
      tokens = extractorEngine.getTokens(luceneDocID, capturedMatch).toSeq
    } yield ArgInfo(argName, tokens)
  }

}
