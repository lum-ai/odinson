package ai.lum.odinson.patterns

import scala.io.Source
import org.scalatest._
import ai.lum.common.DisplayUtils._

import ai.lum.odinson.TestUtils

class TestPatterns extends FlatSpec with Matchers {

  val patternFile = "patternsThatMatch.tsv"
  val source = Source.fromResource(patternFile)
  val lines = source.getLines().toArray

  for (line <- lines.drop(1)) { // skip header
    val Array(pattern, string, allExpected) = line.trim.split("\t")
    val expected = allExpected.split(";", -1)
    pattern should s"find all expected results for ${string.display}" in {
      val ee = TestUtils.mkExtractorEngine(string)
      val results = ee.query(pattern)
      val actual = TestUtils.mkStrings(results, ee)
      actual should equal (expected)
    }
  }

  source.close()

}
