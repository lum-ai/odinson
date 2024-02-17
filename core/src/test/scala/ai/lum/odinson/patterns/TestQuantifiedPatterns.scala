package ai.lum.odinson.patterns

import ai.lum.odinson.test.utils.OdinsonTest

class TestQuantifiedPatterns extends OdinsonTest {

  "Odinson" should "match simple patterns with exact range quantifiers" in {

    // create in-memory engine w/ a single doc w/ a single sentence
    val ee = mkExtractorEngine("step-bros")
    // "John", "C.", "Reilly", "played" ...
    val pattern = "[lemma=play] >nsubj [tag=NNP]{3}"
    val oq = ee.mkQuery(pattern)
    val res = ee.query(oq)

    numMatches(res) should be (1)
    existsMatchWithSpan(odinResults = res, doc = 0, start = 0, end = 3) should be (1)
  }

  it should "match simple patterns with open range quantifiers and a metadata filter" in {

    // create in-memory engine w/ a single doc w/ a single sentence
    val ee = mkExtractorEngine("step-bros")
    // "John", "C.", "Reilly", "played" ...
    val pattern = "[lemma=play] >nsubj [tag=NNP]{,3}"
    val mf = "doc_id == 'step-bros'"
    val oq = ee.mkFilteredQuery(query = pattern, metadataFilter = mf)

    val res = ee.query(oq)

    numMatches(res) should be (1)
    existsMatchWithSpan(odinResults = res, doc = 0, start = 0, end = 3) should be (1)
  }

  it should "match simple patterns with exact range quantifiers (>=2) and a metadata filter" in {

    // create in-memory engine w/ a single doc w/ a single sentence
    val ee = mkExtractorEngine("step-bros")
    // "John", "C.", "Reilly", "played" ...
    val pattern = "[lemma=play] >nsubj [tag=NNP]{3}"
    val mf = "doc_id == 'step-bros'"
    val oq = ee.mkFilteredQuery(query = pattern, metadataFilter = mf)

    val res = ee.query(oq)

    numMatches(res) should be (1)
    existsMatchWithSpan(odinResults = res, doc = 0, start = 0, end = 3) should be (1)
  }
}