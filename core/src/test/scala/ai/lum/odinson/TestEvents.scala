package ai.lum.odinson

import org.scalatest._

class TestEvents extends FlatSpec with Matchers {

  import TestEvents._

  val json = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}"""

  val pattern = """
    trigger = [lemma=eat]
    subject: NP = >nsubj
    object: NP = >dobj
  """

  // extractor engine persists across tests (hacky way)
  val doc = Document.fromJson(json)
  val ee = TestUtils.mkExtractorEngine(doc)

  "Odinson" should "match event with promoted entities" in {
    val pattern = """
      trigger = [lemma=eat]
      subject: ^NP = >nsubj [chunk=B-NP][chunk=I-NP]*
      object: ^NP = >dobj [chunk=B-NP][chunk=I-NP]*
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 1
    val m = results.scoreDocs.head.matches.head
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      Argument("subject", 0, 1),
      Argument("object", 2, 4),
    )
    testEventArguments(m, desiredArgs)
  }

  it should "promote a token when no surface pattern is provided" in {
    val pattern = """
      trigger = [lemma=eat]
      subject: ^NP = >nsubj
      object: ^NP = >dobj
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 1
    val m = results.scoreDocs.head.matches.head
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      Argument("subject", 0, 1),
      Argument("object", 3, 4),
    )
    testEventArguments(m, desiredArgs)
  }

  it should "match when no label is provided" in {
    val pattern = """
      trigger = [lemma=eat]
      subject = >nsubj [chunk=B-NP][chunk=I-NP]*
      object = >dobj [chunk=B-NP][chunk=I-NP]*
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 1
    val m = results.scoreDocs.head.matches.head
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      Argument("subject", 0, 1),
      Argument("object", 2, 4),
    )
    testEventArguments(m, desiredArgs)
  }

  it should "promote a token when no surface pattern is provided and label is not provided" in {
    val pattern = """
      trigger = [lemma=eat]
      subject = >nsubj
      object = >dobj
    """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 1
    val m = results.scoreDocs.head.matches.head
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      Argument("subject", 0, 1),
      Argument("object", 3, 4),
    )
    testEventArguments(m, desiredArgs)
  }

  it should "not throw an exception when it fails" in {
    val pattern = """
      trigger = [lemma=eat]
      subject: ^NP = >nsubj xxx
      object: ^NP = >dobj yyy
    """
    val q = ee.compiler.compileEventQuery(pattern)
    noException should be thrownBy ee.query(q, 1)
  }

  it should "not find event with mentions from the state when the state is empty" in {
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal (0)
  }

  it should "populate the state with NPs" in {
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

  it should "find event with mentions from the state when the state is populated" in {
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 1
    val m = results.scoreDocs.head.matches.head
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      Argument("subject", 0, 1),
      Argument("object", 2, 4),
    )
    testEventArguments(m, desiredArgs)
  }

}

object TestEvents extends FlatSpec with Matchers {

  case class Argument(name: String, start: Int, end: Int) {
    override def toString: String = {
      s"Argument(name=$name, start=$start, end=$end)"
    }
  }

  def testEventTrigger(m: OdinsonMatch, start: Int, end: Int): Unit = {
    m shouldBe an [EventMatch]
    val em = m.asInstanceOf[EventMatch]
    val trigger = em.trigger
    trigger.start shouldEqual start
    trigger.end shouldEqual end
  }

  def testEventArguments(m: OdinsonMatch, desiredArgs: Seq[Argument]): Unit = {

    val matchArgs = for {
      (name, args) <- m.arguments
      arg <- args
    } yield Argument(name, arg.start, arg.end)

    // All desired args should be there, in the right number
    val groupedMatched = matchArgs.groupBy(_.name)
    val groupedDesired = desiredArgs.groupBy(_.name)

    for ((desiredRole, desired) <- groupedDesired) {
      // There should be arg(s) of the desired label
      groupedMatched.keySet should contain (desiredRole)
      // Should have the same number of arguments of that label
      val matchedForThisRole = groupedMatched(desiredRole)
      desired should have size matchedForThisRole.size
      for (d <- desired) {
        matchedForThisRole should contain (d)
      }
    }

    // There shouldn't be any found arguments that we didn't want
    val unwantedArgs = groupedMatched.keySet.diff(groupedDesired.keySet)
    unwantedArgs shouldBe empty

  }

}
