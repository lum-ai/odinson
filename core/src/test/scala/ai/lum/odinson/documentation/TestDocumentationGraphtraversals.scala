package ai.lum.odison.documentation

import org.scalatest._

import ai.lum.odinson.ExtractorEngine
import ai.lum.odinson.BaseSpec
import ai.lum.odinson.events.EventSpec
import ai.lum.odinson.Document

class TestDocumentationGraphTraversals extends EventSpec {
  val json = getJsonDocument("becky-gummy-bears")
  val doc = Document.fromJson(json)
  "Documentation-GraphTraversals" should "work for '>foo' example" in {
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
 
  "Documentation-GraphTraversals" should "work for '<foo' example" in {
    val ee = this.Utils.mkExtractorEngine(doc)
    // what is there should match
    val pattern = """
      trigger = [lemma=gummy]
      object: ^NP = </amod|xcomp/
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val s = ee.query(q)
    // something that is not there should not match
    s.totalHits shouldEqual (1)
    this.testEventTrigger(s.scoreDocs.head.matches.head, start = 2, end = 3)
    val desiredArgs = Seq(
      createArgument("object", 3, 4)
    )
    this.testEventArguments(s.scoreDocs.head.matches.head, desiredArgs)
  }
  
  "Documentation-GraphTraversals" should "work for '<<' example" in {
    val ee = this.Utils.mkExtractorEngine(doc)
    // what is there should match
    val pattern = """
      trigger = [lemma=gummy]
      object: ^NP = <<
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val s = ee.query(q)
    // something that is not there should not match
    s.totalHits shouldEqual (1)
    this.testEventTrigger(s.scoreDocs.head.matches.head, start = 2, end = 3)
    val desiredArgs = Seq(
      createArgument("object", 3, 4)
    )
    this.testEventArguments(s.scoreDocs.head.matches.head, desiredArgs)
  }

  // make sure it matches the correct thing
  "Documentation-GraphTraversals" should "work for '>>' example" in {
    val ee = this.Utils.mkExtractorEngine(doc)
    // what is there should match
    val pattern = """
      trigger = [lemma=bear]
      object: ^NP = >>
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val s = ee.query(q)
    // something that is not there should not match
    s.totalHits shouldEqual (1)
    this.testEventTrigger(s.scoreDocs.head.matches.head, start = 3, end = 4)
    val desiredArgs = Seq(
      createArgument("object", 2, 3)
    )
    this.testEventArguments(s.scoreDocs.head.matches.head, desiredArgs)
  }
  
  "Documentation-GraphTraversals" should "work for '>>{2,3}' example" in {
    val ee = this.Utils.mkExtractorEngine(doc)
    // what is there should match
    val pattern = """
      trigger = [lemma=eat]
      object: ^NP = >>{2,3}
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val s = ee.query(q)
    // something that is not there should not match
    s.totalHits shouldEqual (1)
    this.testEventTrigger(s.scoreDocs.head.matches.head, start = 1, end = 2)
    val desiredArgs = Seq(
      createArgument("object", 2, 3)
    )
    this.testEventArguments(s.scoreDocs.head.matches.head, desiredArgs)
    // this should not match
    val pattern1 = """
      trigger = [lemma=bear]
      object: ^NP = >>{2,3}
    """
    val q1 = ee.compiler.compileEventQuery(pattern1)
    val s1 = ee.query(q1)
    s1.totalHits shouldEqual (0)
  }
} 
