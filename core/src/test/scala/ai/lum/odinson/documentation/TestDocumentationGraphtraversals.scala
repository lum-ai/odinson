package ai.lum.odison.documentation

import org.scalatest._

import ai.lum.odinson.ExtractorEngine
import ai.lum.odinson.BaseSpec
import ai.lum.odinson.events.EventSpec
import ai.lum.odinson.Document

class TestDocumentationGraphTraversals extends EventSpec {
  "Documentation-GraphTraversals" should "work for '<nsubj' example" in {
    val json = getJsonDocument("becky-gummy-bears")
    val doc = Document.fromJson(json)
    val ee = this.Utils.mkExtractorEngine(doc)
    // what is there should match
    val pattern = """
      trigger = [lemma=eat]
      object: ^NP = >dobj
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val s = ee.query(q)
    // something that is not there should not match
    s.totalHits shouldEqual (1)
    this.testEventTrigger(s.scoreDocs.head.matches.head, start = 1, end = 2)
    val desiredArgs = Seq(
      createArgument("object", 3, 4)
    )
    this.testEventArguments(s.scoreDocs.head.matches.head, desiredArgs)
  }




} 
