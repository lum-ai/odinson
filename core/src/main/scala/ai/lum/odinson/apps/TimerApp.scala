package ai.lum.odinson.apsp

import ai.lum.common.TryWithResources.using
import ai.lum.odinson.StateMatch
import ai.lum.odinson.utils.Timer.Timer
import ai.lum.odinson.{ExtractorEngine, Mention, NamedCapture}

import scala.io.Source

/** Queries an index with a rulefile
 *  @author gcgbarbosa
 */
object TimerApp extends App {
  /** receives a [[ai.lum.odinson.Mention]] and prints its content
   */
  def getEventRuleResults(mention: Mention): Unit =  {
    // TODO: assert this is an EventMention
    // print trigger
    println(s"#trigger:<${ee.getString(mention.luceneDocId, mention.odinsonMatch)}>")
    // print named captures
    getNamedCapture(mention.odinsonMatch.namedCaptures, mention.luceneDocId)
  }
  /** receives a list of mentions and calls the single item function
   */
  def getEventRuleResults(mentions: Seq[Mention]): Unit =  {
    mentions.map(m => getEventRuleResults(m))
  }
  /** receives a [[ai.lum.odinson.NamedCapture]] and prints its content
   */
  def getNamedCapture(nc: NamedCapture, luceneDocId: Int): Unit =  {
    println(s"##named-capture ${nc.name}:<${ee.getString(luceneDocId, nc.capturedMatch)}>")
  }
  /** receives a list of named captures and calls the single item function 
   */
  def getNamedCapture(ncs: Seq[NamedCapture], luceneDocId: Int): Unit =  {
    ncs.map(nc => getNamedCapture(nc, luceneDocId))
  }

  println("Starting odinson-tests...")
  val ee = ExtractorEngine.fromConfig
  val queries = {
    val rr = ee.ruleReader
    using(getClass.getResourceAsStream("/grammars/umbc.yml")) { rulesResource =>
      val rules = Source.fromInputStream(rulesResource).getLines.mkString("\n")
      rr.compileRuleString(rules)
    }
  }
  val multipleTimer = new Timer("All runs")
  val singleTimer = new Timer("One run")

  multipleTimer.time {
    Range(0, 10).foreach { i =>
      singleTimer.time {
        ee.extractMentions(queries)
      }
      println(s"$i\t$singleTimer")
    }
  }
  println(multipleTimer)
}
