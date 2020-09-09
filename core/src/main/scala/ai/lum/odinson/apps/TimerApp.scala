package ai.lum.odinson.apsp

import ai.lum.common.TryWithResources.using
import ai.lum.odinson.StateMatch
import ai.lum.odinson.utils.Timer.Timer
import ai.lum.odinson.{ExtractorEngine, Mention, NamedCapture}

import scala.io.Source

object TimerApp extends App {
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
