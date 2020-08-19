package ai.lum.odinson.extra

import org.scalatest._

class TestIndexDocuments extends FlatSpec with Matchers {
  "IndexDocuments" should "run without issues" in {
    // TODO: index files
    AnnotateText.main
    //
    (1) shouldEqual (1)
  }
}
