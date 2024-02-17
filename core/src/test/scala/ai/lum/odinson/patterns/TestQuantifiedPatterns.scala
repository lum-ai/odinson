package ai.lum.odinson.patterns

import ai.lum.odinson.test.utils.OdinsonTest

class TestQuantifiedPatterns extends OdinsonTest {

  "Odinson" should "match simple patterns with exact range quantifiers (>=2)" in {

    // create in-memory engine w/ a single doc w/ a single sentence
    val ee = mkExtractorEngine("step-bros")
    // "John", "C.", "Reilly", "played" ...
    val pattern = "[lemma=play] >nsubj [tag=NNP]{3}"
    val oq = ee.mkQuery(pattern)
    val res = ee.query(oq)

    numMatches(res) should be (1)
    existsMatchWithSpan(odinResults = res, doc = 0, start = 0, end = 3) should be (true)
  }
}