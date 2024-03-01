package ai.lum.odinson.metadata

import ai.lum.odinson.test.utils.OdinsonTest

class TestJoinQueries extends OdinsonTest {

  "Odinson" should "match simple patterns with open range quantifiers and a metadata filter" in {

    // create in-memory engine w/ a single doc w/ a single sentence
    val ee = mkExtractorEngine("step-bros")
    // "John", "C.", "Reilly", "played" ...
    val pattern = "[lemma=play] >nsubj [tag=NNP]{,3}"
    val mf = "doc_id == 'step-bros'"
    val oq = ee.mkFilteredQuery(query = pattern, metadataFilter = mf)

    val res = ee.query(oq)

    numMatches(res) should be (1)
    existsMatchWithSpan(odinResults = res, doc = 0, start = 0, end = 3) should be (true)
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
    existsMatchWithSpan(odinResults = res, doc = 0, start = 0, end = 3) should be (true)
  }

  it should "match simple patterns w/o traversals that use exact range quantifiers (>=2) and a metadata filter" in {

    // create in-memory engine w/ a single doc w/ a single sentence
    val ee = mkExtractorEngine("step-bros")
    // "John", "C.", "Reilly", "played" ...
    val pattern = "[tag=NNP]{3} [lemma=play]"
    val mf = "doc_id == 'step-bros'"
    val oq = ee.mkFilteredQuery(query = pattern, metadataFilter = mf)

    val res = ee.query(oq)
    // println(s"match: (${res.scoreDocs.head.matches.head.start}, ${res.scoreDocs.head.matches.head.end})")
 
    numMatches(res) should be (1)
    existsMatchWithSpan(odinResults = res, doc = 0, start = 0, end = 4) should be (true)
  }
}