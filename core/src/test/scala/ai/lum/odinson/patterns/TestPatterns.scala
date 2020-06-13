package ai.lum.odinson.patterns

import scala.io.Source
import org.scalatest._
import ai.lum.common.DisplayUtils._

import ai.lum.odinson.BaseSpec

class TestPatterns extends BaseSpec {
  //
  val patternFile = "patternsThatMatch.tsv"
  val source = Source.fromResource(patternFile)
  val lines = source.getLines().toArray
  //
  for (line <- lines.drop(1)) { // skip header
    val Array(pattern, string, allExpected) = line.trim.split("\t")
    val expected = allExpected.split(";", -1)
    pattern should s"find all expected results for ${string.display}" in {
      val ee = Utils.mkExtractorEngine(string)
      val q = ee.compiler.mkQuery(pattern)
      val results = ee.query(q)
      val actual = Utils.mkStrings(results, ee)
      
      actual should equal (expected)
    }
  }
  source.close()
}
