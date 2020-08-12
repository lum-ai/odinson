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
    val extractors = ee.ruleReader.compileRuleFile(rules)
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
    val extractors = ee.ruleReader.compileRuleFile(rules)
    val mentions = ee.extractMentions(extractors)
    mentions should have size 2
  }

}
