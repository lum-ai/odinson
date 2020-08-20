package ai.lum.odinson.patterns

import scala.io.Source
import org.scalatest._
import ai.lum.common.DisplayUtils._

import ai.lum.odinson.BaseSpec

class TestNegationOperator extends BaseSpec {
  //
  val doc = getDocument("becky-gummy-bears-v2")
  val ee = Utils.mkExtractorEngine(doc)
  // run [tag=/N.*/ & !lemma=bear]
  "Negation operator" should "should work for [tag=/N.*/ & !lemma=bear]" in {
    val q = ee.compiler.mkQuery("[tag=/N.*/ & !lemma=bear]")
    val results = ee.query(q)
    val actual = Utils.mkStrings(results, ee)
    actual shouldEqual (Array("Becky"))
  }
  // run [tag=/N.*/& lemma!=bear]
  it should "should work for [tag=/N.*/ & lemma!=bear]" in {
    val q = ee.compiler.mkQuery("[tag=/N.*/ & lemma!=bear]")
    val results = ee.query(q)
    val actual = Utils.mkStrings(results, ee)
    actual shouldEqual (Array("Becky"))
  }
}
