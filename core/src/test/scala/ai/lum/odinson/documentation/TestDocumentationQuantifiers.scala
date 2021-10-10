package ai.lum.odison.documentation

import ai.lum.odinson.test.utils.OdinsonTest
import ai.lum.odinson.{ Document, OdinsonMatch }

class TestDocumentationQuantifiers extends OdinsonTest {

  def doc: Document =
    Document.fromJson(
      """{"id":"phosphorylation","metadata":[],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Foo","phosphorylates","bar","bears","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Foo","phosphorylates","bar","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["foo", "phosphorylates","bar","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["PROTEIN","O","PROTEIN","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,2,"dobj"],[1,4,"punct"],[2,3,"amod"]],"roots":[1]}]}]}"""
    )

  "Odinson TestDocumentationQuantifiers" should "work for outgoing optional example" in {
    // get ee
    val ee = mkExtractorEngine(doc)
    // make query
    val pattern = """
      trigger = [lemma=bar]
      object: ^NP = >amod?
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q)
    // since the arg is optional, we can match only the trigger or the arg
    numMatches(results) shouldEqual (2)
    // trigger
    existsMatchWithCapturedMatchSpan(results, doc = 0, start = 3, end = 4) should be(true)
    // argument
    existsMatchWithCapturedMatchSpan(results, doc = 0, start = 2, end = 3) should be(true)

    val pattern1 = """
      trigger = [lemma=bar]
      object: ^NP = >amod
    """
    val q1 = ee.compiler.compileEventQuery(pattern1)
    val s1 = ee.query(q1)
    // here, since the arg isn't optional, we will not match the trigger as a standalone match, only the arg
    numMatches(s1) should be(1)
    // argument
    existsMatchWithSpan(results, doc = 0, start = 2, end = 3) should be(true)
  }

  // []* -- FIXME
//  it should "work for '[]*'" in {
//    val ee = mkExtractorEngineFromText("foo bar")
//    val q = ee.compiler.mkQuery("[]*")
//    val results = ee.query(q)
//
//    numMatches(results) shouldEqual (1)
//    // check if the tokens are correct
//    existsMatchWithSpan(results, doc = 0, start = 0, end = 2) should be (true)
//  }

  //  >>{2,3}
  it should "work for '>>{2,3}'" in {
    val ee = mkExtractorEngine(doc)
    // make query
    val pattern = """
      trigger = [lemma=phosphorylates]
      object: ^NP = >>{2,3}
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q)
    numMatches(results) shouldEqual (1)

    testEventTrigger(results.scoreDocs.head.matches.head, start = 1, end = 2)
    val desiredArgs = Seq(
      ArgumentOffsets("object", 3, 4)
    )
    testArguments(results.scoreDocs.head.matches.head, desiredArgs)
  }

  it should "work for '>amod []'" in {
    val ee = mkExtractorEngine(doc)
    // what is there should match
    val q = ee.mkQuery("(?<foo> [lemma=bar]) >amod []")
    val results = ee.query(q)
    numMatches(results) shouldEqual (1)

    val matchval: OdinsonMatch = getOnlyMatch(results)
    matchval.namedCaptures.length shouldEqual 1
    matchval.namedCaptures.head.name shouldEqual ("foo")
    val nameCapturedVal = matchval.namedCaptures.head.capturedMatch
    nameCapturedVal.start shouldEqual (2)
    nameCapturedVal.end shouldEqual (3)
    // check what
    existsMatchWithSpan(results, doc = 0, start = 3, end = 4) should be(true)
  }
}
