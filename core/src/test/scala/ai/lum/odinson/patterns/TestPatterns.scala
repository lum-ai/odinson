package ai.lum.odinson.patterns

import scala.io.Source
import ai.lum.common.DisplayUtils._
import ai.lum.odinson.utils.TestUtils.OdinsonTest

class TestPatterns extends OdinsonTest {
  //
  val patternFile = "patternsThatMatch.tsv"
  val source = Source.fromResource(patternFile)
  val lines = source.getLines().toArray
  //
  for (line <- lines.drop(1)) { // skip header
    val Array(pattern, string, allExpected) = line.trim.split("\t")
    val expected = allExpected.split(";", -1)
    pattern should s"find all expected results for ${string.display}" in {
      val ee = mkExtractorEngineFromText(string)
      val q = ee.mkQuery(pattern)
      val results = ee.query(q)
      val actual = mkStrings(results, ee)

      actual should equal(expected)
    }
  }
  source.close()
}
