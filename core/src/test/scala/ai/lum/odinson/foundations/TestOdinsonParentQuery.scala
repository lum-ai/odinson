package ai.lum.odinson.foundations

import ai.lum.odinson.{Document => OdinsonDocument}
import ai.lum.odinson.ExtractorEngine
import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.OdinsonQuery
import ai.lum.odinson.test.utils.OdinsonTest

class TestOdinsonParentQuery extends OdinsonTest {

  // keys in ai.lum.odinson.utils.TestUtils.ExampleDocs.json
  val docNames: Seq[String] = Seq("tp-briggs", "tp-pies")
  val docs: Seq[OdinsonDocument] = docNames.map(getDocument)

  val ee: ExtractorEngine = ExtractorEngine.inMemory(defaultConfig, docs)

  "ExtractorEngine" should "not return results when pattern succeeds and parent query fails" in {
    // a simple Odinson pattern
    val pattern = "[lemma=pie]"
    // a query (using Lucene query syntax) that is executed against the document metadata
    val parentQuery: String = "character contains 'NotAWord'"
    val odinsonQuery: OdinsonQuery = ee.mkFilteredQuery(pattern, parentQuery)
    val res: OdinResults = ee.query(odinsonQuery)
    res.totalHits shouldBe 0
    res.scoreDocs should have length 0
  }

  it should "return results when pattern succeeds" in {
    val pattern: String = "[lemma=pie]"
    val odinsonQuery: OdinsonQuery = ee.mkQuery(pattern)
    val res: OdinResults = ee.query(odinsonQuery)
    res.totalHits shouldBe 1
    res.scoreDocs should have length 1
  }

  it should "return results when pattern succeeds and parent query succeeds" in {
    val pattern: String = "[lemma=pie]"
    val parentQuery: String = "character contains '/S.*/ Agent'"
    val odinsonQuery: OdinsonQuery = ee.mkFilteredQuery(pattern, parentQuery)
    val res: OdinResults = ee.query(odinsonQuery)
    res.totalHits shouldBe 1
    res.scoreDocs should have length 1
  }

  it should "match metadata document" in {
    val pattern = "character == 'Special Agent Dale Cooper'"
    val query = ee.compiler.mkParentQuery(pattern)
    val res = ee.index.search(query, 10)
    res.totalHits shouldBe 1
    res.scoreDocs should have length 1
  }

  it should "return results when pattern succeeds and exact string match parent query succeeds" in {
    val pattern: String = "[lemma=pie]"
    val parentQuery: String = """character == "Special Agent Dale Cooper""""
    val odinsonQuery: OdinsonQuery = ee.mkFilteredQuery(pattern, parentQuery)
    val res: OdinResults = ee.query(odinsonQuery)
    res.totalHits shouldBe 1
    res.scoreDocs should have length 1
  }

  it should "return results when pattern succeeds and a two-field exact string match parent query succeeds" in {
    val pattern: String = "[lemma=pie]"
    val parentQuery: String = """character == "Special Agent Dale Cooper" && show == "Twin Peaks""""
    val odinsonQuery: OdinsonQuery = ee.mkFilteredQuery(pattern, parentQuery)
    val res: OdinResults = ee.query(odinsonQuery)
    res.totalHits shouldBe 1
    res.scoreDocs should have length 1
  }

  it should "not return results when pattern succeeds and one field of a two-field exact string match parent query fails (making the full parent query fail)" in {
    val pattern: String = "[lemma=pie]"
    val parentQuery: String =
      """character == "Special Agent Dale Cooper" && show == "Fire Walk With Me""""
    val odinsonQuery: OdinsonQuery = ee.mkFilteredQuery(pattern, parentQuery)
    val res: OdinResults = ee.query(odinsonQuery)
    res.totalHits shouldBe 0
    res.scoreDocs should have length 0
  }

}
