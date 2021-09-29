package ai.lum.odinson.patterns

import ai.lum.odinson.test.utils.OdinsonTest

class TestNegationOperator extends OdinsonTest {
  //
  val doc = getDocument("becky-gummy-bears-v2")
  val ee = mkExtractorEngine(doc)
  // run [tag=/N.*/ & !lemma=bear]
  "Negation operator" should "should work for [tag=/N.*/ & !lemma=bear]" in {
    val q = ee.mkQuery("[tag=/N.*/ & !lemma=bear]")
    val results = ee.query(q)
    val actual = mkStrings(results, ee)
    actual shouldEqual (Array("Becky"))
  }
  // run [tag=/N.*/& lemma!=bear]
  it should "should work for [tag=/N.*/ & lemma!=bear]" in {
    val q = ee.mkQuery("[tag=/N.*/ & lemma!=bear]")
    val results = ee.query(q)
    val actual = mkStrings(results, ee)
    actual shouldEqual (Array("Becky"))
  }
}
