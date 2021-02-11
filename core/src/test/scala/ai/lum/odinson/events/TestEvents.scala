package ai.lum.odinson.events

import ai.lum.odinson.{ EventMatch, MentionsIterator }
import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.OdinsonQuery
import ai.lum.odinson.lucene.search.OdinsonScoreDoc
import ai.lum.odinson.utils.TestUtils.OdinsonTest
import ai.lum.odinson.utils.exceptions.OdinsonException

class TestEvents extends OdinsonTest {

  // extractor engine persists across tests (hacky way)
  val ee = mkExtractorEngine("becky-gummy-bears")

  "Odinson" should "match event with promoted entities" in {
    val pattern =
      """
    trigger = [lemma=eat]
    subject: ^NP = >nsubj [chunk=B-NP][chunk=I-NP]*
    object: ^NP = >dobj [chunk=B-NP][chunk=I-NP]*
  """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal(1)
    results.scoreDocs.head.matches should have size 1
    val m = results.scoreDocs.head.matches.head
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      ArgumentOffsets("subject", 0, 1),
      ArgumentOffsets("object", 2, 4)
    )
    testArguments(m, desiredArgs)
  }

  it should "respect quantifiers in arguments" in {
    val pattern =
      """
    trigger = [lemma=eat]
    subject: ^NP = >nsubj [chunk=B-NP][chunk=I-NP]*
    object: ^NP = >dobj gummy? bears
  """
    // the above rule should match {bears} and {gummy bears}
    // and then keep only {gummy bears} because the quantifier `?` is greedy
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal(1)
    results.scoreDocs.head.matches should have size 1
    val m = results.scoreDocs.head.matches.head
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      ArgumentOffsets("subject", 0, 1),
      ArgumentOffsets("object", 2, 4)
    )
    testArguments(m, desiredArgs)
  }

  it should "have only one argument metadata with any given name" in {
    ee.clearState()
    val rule =
      """
        |rules:
        |  - name: testrule
        |    type: event
        |    pattern: |
        |      trigger = [lemma=eat]
        |      subject: ^NP = >nsubj [chunk=B-NP][chunk=I-NP]*
        |      object: ^NP = >dobj gummy? bears
  """.stripMargin
    // the above rule should match {bears} and {gummy bears}
    // and then keep only {gummy bears} because the quantifier `?` is greedy
    val extractors = ee.compileRuleString(rule)
    val mentions = ee.extractNoState(extractors).toArray
    // the main event and the two args
    mentions.length should equal(3)

    val em = mentions.map(_.odinsonMatch).collect { case m: EventMatch =>
      m
    }.head
    val argMetadataNames = em.argumentMetadata.toSeq.map(_.name)
    // the length of this list should not change if it goes to a set
    argMetadataNames.length should be(argMetadataNames.toSet.size)

    ee.clearState()
  }

  it should "promote a token when no surface pattern is provided" in {
    val pattern =
      """
    trigger = [lemma=eat]
    subject: ^NP = >nsubj
    object: ^NP = >dobj
  """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal(1)
    results.scoreDocs.head.matches should have size 1
    val m = results.scoreDocs.head.matches.head
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      ArgumentOffsets("subject", 0, 1),
      ArgumentOffsets("object", 3, 4)
    )
    testArguments(m, desiredArgs)
    ee.clearState()
  }

  it should "match when no label is provided" in {
    val pattern =
      """
    trigger = [lemma=eat]
    subject = >nsubj [chunk=B-NP][chunk=I-NP]*
    object = >dobj [chunk=B-NP][chunk=I-NP]*
  """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal(1)
    results.scoreDocs.head.matches should have size 1
    val m = results.scoreDocs.head.matches.head
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      ArgumentOffsets("subject", 0, 1),
      ArgumentOffsets("object", 2, 4)
    )
    testArguments(m, desiredArgs)
    ee.clearState()
  }

  it should "promote a token when no surface pattern is provided and label is not provided" in {
    val pattern =
      """
    trigger = [lemma=eat]
    subject = >nsubj
    object = >dobj
  """
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal(1)
    results.scoreDocs.head.matches should have size 1
    val m = results.scoreDocs.head.matches.head
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      ArgumentOffsets("subject", 0, 1),
      ArgumentOffsets("object", 3, 4)
    )
    testArguments(m, desiredArgs)
    ee.clearState()
  }

  it should "not throw an exception when it fails" in {
    val pattern =
      """
    trigger = [lemma=eat]
    subject: ^NP = >nsubj xxx
    object: ^NP = >dobj yyy
  """
    val q = ee.compiler.compileEventQuery(pattern)
    noException should be thrownBy ee.query(q, 1)
    ee.clearState()
  }

  val pattern =
    """
  trigger = [lemma=eat]
  subject: NP = >nsubj
  object: NP = >dobj
  """

  it should "not find event with mentions from the state when the state is empty" in {
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal(0)
    ee.clearState()
  }

  it should "populate the state with NPs" in {
    val query = ee.compiler.mkQuery("[chunk=B-NP][chunk=I-NP]*")
    val results = ee.query(query)
    results.totalHits should equal(1)
    results.scoreDocs.head.matches should have size 2
    ee.clearState()
  }

  it should "find event with mentions from the state when the state is populated" in {
    val q1 = ee.compiler.mkQuery("[chunk=B-NP][chunk=I-NP]*")
    val q2 = ee.compiler.compileEventQuery(pattern)

    // The ee.query no longer adds to the state on its own, so this helper is being used.
    def localQuery(
      odinsonQuery: OdinsonQuery,
      labelOpt: Option[String] = None,
      nameOpt: Option[String] = None,
      n: Int,
      after: OdinsonScoreDoc,
      disableMatchSelector: Boolean
    ): OdinResults = {
      val odinResults = ee.query(odinsonQuery, n, after, disableMatchSelector)
      val odinMentionsIterator =
        new MentionsIterator(labelOpt, nameOpt, odinResults, mruIdGetter)

      ee.state.addMentions(odinMentionsIterator)
      odinResults
    }

    // This query adds to the state, so it is helped by the localQuery.
    val results1 = localQuery(
      q1,
      labelOpt = Some("NP"),
      nameOpt = None,
      1,
      after = null,
      disableMatchSelector = false
    )
    results1.totalHits should equal(1)
    results1.scoreDocs.head.matches should have size 2

    // This query only needs to read from the state.
    val results2 = ee.query(q2, 1, after = null, disableMatchSelector = false)
    results2.totalHits should equal(1)
    results2.scoreDocs.head.matches should have size 1

    val m = results2.scoreDocs.head.matches.head
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      ArgumentOffsets("subject", 0, 1),
      ArgumentOffsets("object", 2, 4)
    )
    testArguments(m, desiredArgs)
    ee.clearState()
  }

  it should "retrieve events properly from the state" in {

    val rules =
      """
        |rules:
        |  - name: bears-rule
        |    label: Bear
        |    type: event
        |    priority: 1
        |    pattern: |
        |      trigger = bears
        |      bearType = >amod []
        |
        |  - name: eating-rule
        |    label: Consumption
        |    type: event
        |    priority: 2
        |    pattern: |
        |      trigger = [lemma=eat]
        |      subject: ^NP = >nsubj []
        |      object: Bear = >dobj
     """.stripMargin

    ee.clearState()
    val extractors = ee.ruleReader.compileRuleString(rules)
    val mentions = ee.extractMentions(extractors).toArray

    mentions should have size (4)

    // Bear event
    val bears = getMentionsWithLabel(mentions, "Bear")
    bears should have size (1)
    val bear = bears.head
    bear.arguments.keySet should have size 1
    val bearType = bear.arguments("bearType")
    bearType should have size (1)
    val desiredBearArg = Seq(ArgumentOffsets("bearType", 2, 3))
    testArguments(bear.odinsonMatch, desiredBearArg)

    // Consumption Event, which should include the Bear event above as an arg, from the State
    val eats = getMentionsWithLabel(mentions, "Consumption")
    eats should have size 1
    val eat = eats.head
    eat.arguments.keySet should have size 2
    val objs = eat.arguments("object")
    objs should have size 1
    // Make sure the obj has all the things that the bear had above
    val obj = objs.head
    obj.arguments.keySet should have size 1
    val objType = obj.arguments("bearType")
    objType should have size (1)
    testArguments(obj.odinsonMatch, desiredBearArg)
    ee.clearState()
  }

  // We can revisit the semantics here if desired
  it should "not allow two arguments with the same name" in {

    val rules =
      """
        |rules:
        |  - name: bears-rule
        |    label: Bear
        |    type: event
        |    priority: 1
        |    pattern: |
        |      trigger = bears
        |      ARG = >amod []
        |      ARG = <dobj []
     """.stripMargin

    a[OdinsonException] should be thrownBy ee.ruleReader.compileRuleString(
      rules
    )

  }

  ee.close()

}
