package ai.lum.odinson.patterns

import ai.lum.odinson.test.utils.OdinsonTest

class TestNegationOperator extends OdinsonTest {
  //
  val doc = getDocument("becky-gummy-bears-v2")
  val ee = mkExtractorEngine(doc)
  // run [tag=/N.*/ & !lemma=bear]
  "Negation operator" should "work for [tag=/N.*/ & !lemma=bear]" in {
    val q = ee.mkQuery("[tag=/N.*/ & !lemma=bear]")
    val results = ee.query(q)
    val actual = mkStrings(results, ee.dataGatherer)
    actual shouldEqual (Array("Becky"))
  }
  // run [tag=/N.*/& lemma!=bear]
  it should "work for [tag=/N.*/ & lemma!=bear]" in {
    val q = ee.mkQuery("[tag=/N.*/ & lemma!=bear]")
    val results = ee.query(q)
    val actual = mkStrings(results, ee.dataGatherer)
    actual shouldEqual (Array("Becky"))
  }

  it should "work for [lemma!=bear]" in {
    val q = ee.mkQuery("[lemma!=bear]")
    val results = ee.query(q)
    val actual = mkStrings(results, ee.dataGatherer)
    actual shouldEqual (Array("Becky", "ate", "gummy", "."))
  }

  it should "work for [!tag=/NN.*/]" in {
    val q = ee.mkQuery("[!tag=/NN.*/]")
    val results = ee.query(q)
    val actual = mkStrings(results, ee.dataGatherer)
    actual shouldEqual (Array("ate", "gummy", "."))
  }
}
