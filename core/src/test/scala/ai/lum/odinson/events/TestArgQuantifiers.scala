package ai.lum.odinson.events

import org.scalatest._

import ai.lum.odinson.{Document}

class TestArgQuantifiers extends EventSpec {

  val json = """{"id":"48fb577b-f5ba-4e16-864f-f8ba20ba9cfa","metadata":[],"sentences":[{"numTokens":8,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["The","consumption","of","gummy","bears","and","donuts","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["The","consumption","of","gummy","bears","and","donuts","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["DT","NN","IN","NN","NNS","CC","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["the","consumption","of","gummy","bear","and","donut","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["O","O","O","B-dessert","I-dessert","O","B-dessert","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","I-NP","B-PP","B-NP","I-NP","O","B-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"det"],[1,4,"nmod_of"],[1,7,"punct"],[4,2,"case"],[4,3,"compound"],[4,5,"cc"],[4,6,"conj"]],"roots":[1]}]}]}"""

  val doc = getDocumentFromJson(json)
  val ee = Utils.mkExtractorEngine(doc)

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
    val desiredArgs1 = Seq(Argument("theme", 3, 5))
    val desiredArgs2 = Seq(Argument("theme", 6, 7))
    testEventArguments(m1, desiredArgs1)
    testEventArguments(m2, desiredArgs2)
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
    val desiredArgs1 = Seq(Argument("theme", 3, 5))
    val desiredArgs2 = Seq(Argument("theme", 6, 7))
    testEventArguments(m1, desiredArgs1)
    testEventArguments(m2, desiredArgs2)
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
      Argument("theme", 3, 5),
      Argument("theme", 6, 7),
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
      Argument("theme", 3, 5),
      Argument("theme", 6, 7),
    )
    testEventArguments(m, desiredArgs)
  }
}

