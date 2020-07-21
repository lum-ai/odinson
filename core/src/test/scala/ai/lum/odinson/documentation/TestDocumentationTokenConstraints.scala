package ai.lum.odison.documentation

import org.scalatest._

import ai.lum.odinson.ExtractorEngine
import ai.lum.odinson.BaseSpec

class TestDocumentationTokenConstraints extends BaseSpec {
  "Documentation-TokenConstraints" should "work for 'Example'" in {
    val ee = this.Utils.mkExtractorEngine("The dog barks")
    // what is there should match
    val q = ee.compiler.mkQuery("dog")
    val s = ee.query(q)
    s.totalHits shouldEqual (1)
    // something that is not there should not match
    val q1 = ee.compiler.mkQuery("cat")
    val s1 = ee.query(q1)
    s1.totalHits shouldEqual (0)
  }

}
