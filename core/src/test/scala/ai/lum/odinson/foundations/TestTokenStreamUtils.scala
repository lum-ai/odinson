package ai.lum.odinson.foundations

import ai.lum.odinson.lucene.analysis.TokenStreamUtils
import ai.lum.odinson.utils.TestUtils.OdinsonTest

class TestTokenStreamUtils extends OdinsonTest {

  behavior of "TokenStreamUtils"

  it should "not get more fields than requested when accessing the Document" in {
    val doc = getDocument("becky-gummy-bears-v2")
    val ee = extractorEngineWithConfigValue(doc, "odinson.index.storedFields", Seq("raw", "lemma"))

    val tokens =
      TokenStreamUtils.getTokensFromMultipleFields(0, Set("raw"), ee.indexReader, ee.analyzer)
    tokens.keySet should contain only ("raw")

    val luceneDoc = TokenStreamUtils.getDoc(0, Set("raw"), ee.indexReader)
    luceneDoc.getFields should have size (1)
    noException shouldBe thrownBy(luceneDoc.getField("raw"))
    luceneDoc.getField("lemma") shouldBe (null)
  }

}
