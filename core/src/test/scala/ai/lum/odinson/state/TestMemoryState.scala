package ai.lum.odinson.state

import ai.lum.odinson.test.utils.OdinsonTest

class TestMemoryState extends OdinsonTest {

  val docGummy = getDocument("becky-gummy-bears-v2")

  val eeGummyMemory = extractorEngineWithSpecificState(docGummy, "memory")

  "MemoryState" should "return mentions" in {
    val rules = """
        |rules:
        |  - name: gummy-rule
        |    label: Bear
        |    type: basic
        |    priority: 1
        |    pattern: |
        |      gummy
        |
        |  - name: eating-rule
        |    label: Consumption
        |    type: event
        |    priority: 2
        |    pattern: |
        |      trigger = [lemma=eat]
        |      subject: ^NP = >nsubj []
        |      object: ^NP = >dobj []
        |
        |  - name: nomatch-rule
        |    label: Gummy
        |    type: event
        |    priority: 2
        |    pattern: |
        |      trigger = bears
        |      arg: Bear = >amod
       """.stripMargin

    val extractors = eeGummyMemory.ruleReader.compileRuleString(rules)
    val mentions = eeGummyMemory.extractMentions(extractors).toArray

    // the 3 main extractions + 2 promoted args
    mentions should have size (5)

    getMentionsWithLabel(mentions, "Gummy") should have size (1)

  }

}
