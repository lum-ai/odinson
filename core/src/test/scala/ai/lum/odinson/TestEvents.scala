package ai.lum.odinson

import org.scalatest._

class TestEvents extends FlatSpec with Matchers {

  val json = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","incomingEdges":[[[1,"nsubj"]],[],[[3,"amod"]],[[1,"dobj"]],[[1,"punct"]]],"outgoingEdges":[[],[[0,"nsubj"],[3,"dobj"],[4,"punct"]],[],[[2,"amod"]],[]],"roots":[1]}]}]}"""

  val pattern = """
    trigger = [lemma=eat]
    subject: NP = >nsubj
    object: NP = >dobj
  """

  // extractor engine persists across tests (hacky way)
  val doc = Document.fromJson(json)
  val ee = TestUtils.mkExtractorEngine(doc)

  "Document" should "contain NPs" in {
    val results = ee.query("[chunk=B-NP][chunk=I-NP]*")
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 2
    for {
      scoreDoc <- results.scoreDocs
      m <- scoreDoc.matches
    } {
      ee.state.addMention(
        docBase = scoreDoc.segmentDocBase,
        docId = scoreDoc.segmentDocId,
        label = "NP",
        startToken = m.start,
        endToken = m.end,
      )
    }
    ee.state.index()
  }

  it should "contain Becky eating gummy bears" in {
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 1
    val m = results.scoreDocs.head.matches.head
    testEventTrigger(m, start = 1, end = 2)
    m.arguments.keys should contain ("subject")
    m.arguments("subject") should have size 1
    val subject = m.arguments("subject").head
    subject.start should be (0)
    subject.end should be (1)
    m.arguments.keys should contain ("object")
    m.arguments("object") should have size 1
    val `object` = m.arguments("object").head
    `object`.start should be (2)
    `object`.end should be (4)
  }

  def testEventTrigger(m: OdinsonMatch, start: Int, end: Int): Unit = {
    m shouldBe an [EventMatch]
    val em = m.asInstanceOf[EventMatch]
    val trigger = em.trigger
    trigger.start shouldEqual start
    trigger.end shouldEqual end
  }

}
