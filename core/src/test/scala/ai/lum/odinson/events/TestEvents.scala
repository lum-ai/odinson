package ai.lum.odinson.events


class TestEvents extends EventSpec {

  // extractor engine persists across tests (hacky way)
  def doc = getDocument("becky-gummy-bears")
  def ee = Utils.mkExtractorEngine(doc)

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
      createArgument("subject", 0, 1),
      createArgument("object", 2, 4),
    )
    testEventArguments(m, desiredArgs)
  }

  it should "respect quantifiers in arguments" in {
    val pattern = """
      trigger = [lemma=eat]
      subject: ^NP = >nsubj [chunk=B-NP][chunk=I-NP]*
      object: ^NP = >dobj gummy? bears
    """
    // the above rule should match {bears} and {gummy bears}
    // and then keep only {gummy bears} because the quantifier `?` is greedy
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 1
    val m = results.scoreDocs.head.matches.head
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      createArgument("subject", 0, 1),
      createArgument("object", 2, 4),
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
      createArgument("subject", 0, 1),
      createArgument("object", 3, 4),
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
      createArgument("subject", 0, 1),
      createArgument("object", 2, 4),
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
      createArgument("subject", 0, 1),
      createArgument("object", 3, 4),
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
  
  val pattern = """
    trigger = [lemma=eat]
    subject: NP = >nsubj
    object: NP = >dobj
  """
  
  it should "not find event with mentions from the state when the state is empty" in {
    val q = ee.compiler.compileEventQuery(pattern)
    val results = ee.query(q, 1)
    results.totalHits should equal (0)
  }

  it should "populate the state with NPs" in {
    val query = ee.compiler.mkQuery("[chunk=B-NP][chunk=I-NP]*")
    val results = ee.query(query)
    results.totalHits should equal (1)
    results.scoreDocs.head.matches should have size 2
  }  

  it should "find event with mentions from the state when the state is populated" in {
    val q1 = ee.compiler.mkQuery("[chunk=B-NP][chunk=I-NP]*")
    val q2 = ee.compiler.compileEventQuery(pattern)

    ee.stateFactory.usingState { state =>
      val results1 = ee.query(q1, labelOpt = Some("NP"), nameOpt = None, 1, after = null, disableMatchSelector = false, state)
      results1.totalHits should equal (1)
      results1.scoreDocs.head.matches should have size 2

      val results2 = ee.query(q2, labelOpt = None, nameOpt = None, 1, after = null, disableMatchSelector = false, state)
      results2.totalHits should equal(1)
      results2.scoreDocs.head.matches should have size 1

      val m = results2.scoreDocs.head.matches.head
      // test trigger
      testEventTrigger(m, start = 1, end = 2)
      // test arguments
      val desiredArgs = Seq(
        createArgument("subject", 0, 1),
        createArgument("object", 2, 4),
      )
      testEventArguments(m, desiredArgs)
    }
  }

  it should "retrieve events properly from the state" in {

    val rules = """
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

    val extractors = ee.ruleReader.compileRuleString(rules)
    val mentions = ee.extractMentions(extractors)

    mentions should have size(2)

    // Bear event
    val bears = mentions.filter(_.label.get == "Bear")
    bears should have size(1)
    val bear = bears.head
    bear.arguments.keySet should have size 1
    val bearType = bear.arguments("bearType")
    bearType should have size(1)
    val desiredBearArg = Seq(createArgument("bearType", 2, 3))
    testEventArguments(bear.odinsonMatch, desiredBearArg)

    // Consumption Event, which should include the Bear event above as an arg, from the State
    val eats = mentions.filter(_.label.get == "Consumption")
    eats should have size 1
    val eat = eats.head
    eat.arguments.keySet should have size 2
    val objs = eat.arguments("object")
    objs should have size 1
    // Make sure the obj has all the things that the bear had above
    val obj = objs.head
    obj.arguments.keySet should have size 1
    val objType = obj.arguments("bearType")
    objType should have size(1)
    testEventArguments(obj.odinsonMatch, desiredBearArg)
  }

}
