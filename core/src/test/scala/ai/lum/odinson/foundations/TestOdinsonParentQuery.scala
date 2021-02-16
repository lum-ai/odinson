package ai.lum.odinson.foundations

import ai.lum.odinson.{ Document => OdinsonDocument }
import ai.lum.odinson.ExtractorEngine
import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.OdinsonQuery
import ai.lum.odinson.utils.TestUtils.OdinsonTest

class TestOdinsonParentQuery extends OdinsonTest {

  // keys in ai.lum.odinson.utils.TestUtils.ExampleDocs.json
  val docNames: Seq[String] = Seq("tp-briggs", "tp-pies")
  val docs: Seq[OdinsonDocument] = docNames.map(getDocument)

  val ee: ExtractorEngine = ExtractorEngine.inMemory(defaultConfig, docs)

  def combineQueries(odinsonPattern: String, parentQuery: String): OdinsonQuery =
    ee.compiler.mkQuery(
      pattern = odinsonPattern,
      parentPattern = parentQuery
    )

  "ExtractorEngine" should "not return results when pattern succeeds and parent query fails" in {
    // a simple Odinson pattern
    val pattern = "[lemma=pie]"
    // a query (using Lucene query syntax) that is executed against the document metadata
    val parentQuery: String = "character:Major.*"
    val odinsonQuery: OdinsonQuery = combineQueries(pattern, parentQuery)
    val res: OdinResults = ee.query(odinsonQuery)
    res.totalHits shouldBe 0
    res.scoreDocs should have length 0
  }

  it should "return results when pattern succeeds and parent query succeeds" in {
    val pattern: String = "[lemma=pie]"
    val parentQuery: String = "character:Special Agent*"
    val odinsonQuery: OdinsonQuery = combineQueries(pattern, parentQuery)
    val res: OdinResults = ee.query(odinsonQuery)
    res.totalHits shouldBe 1
    res.scoreDocs should have length 1
  }

}
