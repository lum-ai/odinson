package ai.lum.odinson

import org.scalatest._

class TestFields extends FlatSpec with Matchers {

  val json = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}"""

  // extractor engine persists across tests (hacky way)
  val doc = Document.fromJson(json)
  val ee = TestUtils.mkExtractorEngine(doc)

  "Odinson" should "be case insensitive on the norm field (implicitly)" in {
    val q = ee.compiler.mkQuery("ATE")
    val results = ee.query(q)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 1
  }

  it should "be case insensitive on the norm field (explicitly)" in {
    val q = ee.compiler.mkQuery("[norm=ATE]")
    val results = ee.query(q)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 1
  }

  it should "match with correct case on the raw field" in {
    val q = ee.compiler.mkQuery("[raw=ate]")
    val results = ee.query(q)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 1
  }

  it should "not match with wrong case on the raw field" in {
    val q = ee.compiler.mkQuery("[raw=ATE]")
    val results = ee.query(q)
    results.totalHits should equal (0)
  }

}
