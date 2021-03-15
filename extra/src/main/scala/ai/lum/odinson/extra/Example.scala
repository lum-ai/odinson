package ai.lum.odinson.extra

import java.io.File

import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.odinson.serialization.JsonSerializer
import ai.lum.odinson.utils.DisplayUtils.displayMention
import ai.lum.odinson.utils.SituatedStream
import ai.lum.odinson.ExtractorEngine
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
  args: Seq[ArgInfo]
) {
  def toJson: String = write(this)
}

object MentionInfo { implicit val rw: ReadWriter[MentionInfo] = macroRW }

// Wrapper for named captures (i.e., arguments) for use in the example,
// again not intended to be comprehensive.
case class ArgInfo(role: String, tokens: Seq[String]) {
  def toJson: String = write(this)
}

object ArgInfo { implicit val rw: ReadWriter[ArgInfo] = macroRW }

object Example extends App with LazyLogging {

  // Specify paths and settings in the local config file
  val config = ConfigFactory.load()
  val outputFile: File = config[File]("odinson.extra.outputFile")
  val rulesFile: String = config[String]("odinson.extra.rulesFile")
  val rulesStream = SituatedStream.fromResource(rulesFile)

  // Initialize the extractor engine, using the index specified in the config
  val extractorEngine = ExtractorEngine.fromConfig()
  val extractors = extractorEngine.ruleReader.compileRuleStream(rulesStream)
  println(s"Found ${extractors.length} extractors")

  // Extract Mentions
  val mentions = extractorEngine.extractMentions(extractors).toArray
  mentions.foreach(displayMention(_, extractorEngine))

  // Export Mentions (here as json lines)
  val jsonSerializer =
    new JsonSerializer(verbose = JsonSerializer.DISPLAY, engine = Some(extractorEngine))

  val serialized = jsonSerializer.asJsonLines(mentions)
  outputFile.writeString(serialized.mkString("\n"))

}
