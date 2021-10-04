package ai.lum.odinson.patterns

import ai.lum.odinson.utils.TestUtils.OdinsonTest

class TestCustomTokenization extends OdinsonTest {

  def ee = mkExtractorEngine("internal-space")

  "Odinson" should "find a match for a token with whitespace" in {
    val pattern = """[raw="Figure 3"]"""
    val q = ee.mkQuery(pattern)
    val results = ee.query(q, 5)
    results.totalHits should equal(1)

    // maintain original token indices
//    val targetWord = results.scoreDocs.head.matches.
    val interval = results.scoreDocs.head.matches.head.tokenInterval
    interval.start should equal(25)
    interval.end should equal(26)
    ee.clearState()
  }
}
