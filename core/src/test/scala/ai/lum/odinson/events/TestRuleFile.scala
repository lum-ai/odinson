package ai.lum.odinson.events

class TestRuleFile extends EventSpec {
  // extractor engine persists across tests (hacky way)
  def doc = getDocument("becky-gummy-bears-v2")
  def ee = Utils.mkExtractorEngine(doc)

  "Odinson" should "match event with rules defined in a rule file" in {
    val rules = """
      |vars:
      |  chunk: "[chunk=B-NP][chunk=I-NP]*"
      |
      |rules:
      |  - name: testrule
      |    type: event
      |    pattern: |
      |      trigger = [lemma=eat]
      |      subject: ^NP = >nsubj ${chunk}
      |      object: ^NP = >dobj ${chunk}
    """.stripMargin
    val extractors = ee.ruleReader.compileRuleString(rules)
    val mentions = ee.extractMentions(extractors)
    mentions should have size 1
    val m = mentions.head.odinsonMatch
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      createArgument("subject", 0, 1),
      createArgument("object", 2, 4),
    )
    testEventArguments(m, desiredArgs)
  }

  it should "correctly handle list vars" in {
    // Leonardo leads, Donatello does machines (That's a fact, jack!)
    val doc = getDocument("ninja-turtles")
    val ee = Utils.mkExtractorEngine(doc)
    val rules =
      """
        |vars:
        |  turtle:
        |     - leonardo
        |     - donatello
        |     - raphael
        |     - michelangelo
        |
        |rules:
        |  - name: "turtle-power-var"
        |    label: MutantTurtle
        |    type: basic
        |    pattern: |
        |      [norm=/${turtle}/]
       """.stripMargin
    val extractors = ee.compileRuleString(rules)
    val mentions = ee.extractMentions(extractors)
    mentions should have size 2
  }

  it should "allow rules to be imported in a master rule file" in {
    val masterPath = "/testGrammar/testMaster.yml"
    val extractors = ee.compileRuleResource(masterPath)
    val mentions = ee.extractMentions(extractors)
    mentions should have size 1
    // Tests that the variables from the master file propagate
    mentions.head.foundBy should be("testRuleImported-IMPORT_LABEL")
  }

  // todo:
  //  1: import in a string should throw an exception
  //  2: a string w/o import should not throw an exception
  //  3: test resources with absolute and relative paths (with .. notation)
  //  4: test filesystem resources -- write temp directory with files, put a grammar there, delete when done (if possible)
  //  5: test that the hard-coded > import > parent > local

}
