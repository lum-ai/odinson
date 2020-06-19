package ai.lum.odinson.events

import org.scalatest._

import ai.lum.odinson.{Document}

class TestArgQuantifiers extends EventSpec {
  val json = getJsonDocument("1")

  def doc = getDocumentFromJson(json)
  def ee = Utils.mkExtractorEngine(doc)

  def desiredArgs35: Seq[Argument] = Seq(createArgument("theme", 3, 5))
  def desiredArgs67: Seq[Argument] = Seq(createArgument("theme", 6, 7))

  "Odinson" should "find two events with one required theme each" in {
    val pattern = """
      trigger = consumption
      theme: ^dessert = >nmod_of >conj? [entity=B-dessert][entity=I-dessert]*
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 5)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 2
    val Array(m1, m2) = results.scoreDocs.head.matches
    // test trigger
    testEventTrigger(m1, start = 1, end = 2)
    testEventTrigger(m2, start = 1, end = 2)
    // test arguments
    testEventArguments(m1, desiredArgs35)
    testEventArguments(m2, desiredArgs67)
  }
  
  it should "find two events with one optional theme each" in {
    val pattern = """
      trigger = consumption
      theme: ^dessert? = >nmod_of >conj? [entity=B-dessert][entity=I-dessert]*
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 5)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 2
    val Array(m1, m2) = results.scoreDocs.head.matches
    // test trigger
    testEventTrigger(m1, start = 1, end = 2)
    testEventTrigger(m2, start = 1, end = 2)
    // test arguments
    testEventArguments(m1, desiredArgs35)
    testEventArguments(m2, desiredArgs67)
  }
  
  it should "find one event with two required themes" in {
    val pattern = """
      trigger = consumption
      theme: ^dessert+ = >nmod_of >conj? [entity=B-dessert][entity=I-dessert]*
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 5)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 1
    val Array(m) = results.scoreDocs.head.matches
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      desiredArgs35.head,
      desiredArgs67.head
    )
    testEventArguments(m, desiredArgs)
  }
  
  it should "find one event with two optional themes" in {
    val pattern = """
      trigger = consumption
      theme: ^dessert* = >nmod_of >conj? [entity=B-dessert][entity=I-dessert]*
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 5)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 1
    val Array(m) = results.scoreDocs.head.matches
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      desiredArgs35.head,
      desiredArgs67.head
    )
    testEventArguments(m, desiredArgs)
  }
}

