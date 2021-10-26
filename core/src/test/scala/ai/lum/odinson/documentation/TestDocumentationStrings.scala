package ai.lum.odinson.documentation

import ai.lum.odinson.test.utils.OdinsonTest
import ai.lum.odinson.{ Document, OdinsonMatch }

class TestDocumentationString extends OdinsonTest {

  def doc: Document =
    Document.fromJson(
      """{"id":"george-what?-bears","metadata":[],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["George","and","dog","bears","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["George","and","dog","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["george","and","dog","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["foo:bar","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","I-NP","I-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"nmod_foo"]],"roots":[1]}]}]}"""
    )

  // - does not need quotes
  "Odinson StringQueries from docs" should "work with - no quotes" in {
    val ee = mkExtractorEngine(doc)
    val q = ee.mkQuery("[chunk=B-NP]")

    val s = ee.query(q)
    numMatches(s) shouldEqual (1)
  }
  // : does not need quotes
  it should "work with : no quotes" in {
    val ee = mkExtractorEngine(doc)
    val q = ee.mkQuery("[entity=foo:bar]")
    val s = ee.query(q)
    numMatches(s) shouldEqual (1)
  }
  // "3:10" to Yuma
  it should "work with quoted stuff" in {
    val ee = mkExtractorEngineFromText("lala lala 3:10 to Yuma")
    val q = ee.mkQuery("\"3:10\" to Yuma")
    val s = ee.query(q)
    numMatches(s) shouldEqual (1)
  }
  it should "work with regex for syntax" in {
    val ee = mkExtractorEngine(doc)
    val q = ee.mkQuery("(?<foo> [word=bears]) >/nmod_.*/ []")
    val s = ee.query(q)
    numMatches(s) shouldEqual (1)
    val matchval: OdinsonMatch = getOnlyMatch(s)
    matchval.namedCaptures.length shouldEqual 1
    matchval.namedCaptures.head.name shouldEqual ("foo")
    val nameCapturedVal = matchval.namedCaptures.head.capturedMatch
    nameCapturedVal.start shouldEqual (3)
    nameCapturedVal.end shouldEqual (4)
    // check what
    existsMatchWithSpan(s, doc = 0, start = 2, end = 3)
  }
}
