package ai.lum.odinson.utils

import ai.lum.odinson.{ EventMatch, ExtractorEngine, Mention }

object DisplayUtils {

  def displayMention(mention: Mention, ee: ExtractorEngine): Unit = {
    // Get the OdinsonMatch
    val odinsonMatch = mention.odinsonMatch
    // Get the text of the match
    val luceneDocID = mention.luceneDocId
    val text = ee.getStringForSpan(luceneDocID, odinsonMatch)
    println()
    println("-" * 30)
    println(s"Mention Text: $text")
    println(s"Label: ${mention.label.getOrElse("none")}")
    // Get the name of the rule that found the extraction
    val foundBy = mention.foundBy
    println(s"Found By: $foundBy")
    odinsonMatch match {
      case em: EventMatch =>
        // Print the trigger and the arguments
        println(s"  Trigger: ${ee.getStringForSpan(luceneDocID, em.trigger)}")
      case _ => ()
    }
    // If there are args, print them too
    if (mention.arguments.nonEmpty) {
      println("  Args:")
      val stringified = mention.arguments.mapValues(ms =>
        ms.map(m => (ee.getStringForSpan(luceneDocID, m.odinsonMatch), m.label))
      )
      stringified foreach { case (argName, matched) =>
        matched foreach { case (s, label) =>
          println(s"    * $argName [${label.getOrElse("no label")}]: $s")
        }
      }
    }
  }

}
