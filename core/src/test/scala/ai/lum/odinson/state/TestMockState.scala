package ai.lum.odinson.state

import ai.lum.odinson.test.utils.OdinsonTest

class TestMockState extends OdinsonTest {

  val docGummy = getDocument("becky-gummy-bears-v2")

  val eeGummy = extractorEngineWithSpecificState(docGummy, "mock")

  "MockState" should "return mentions that don't rely on state-based retrieval" in {
    val rules = """
          |rules:
          |  - name: gummy-rule
          |    label: Gummy
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
          |    label: GummyBear
          |    type: event
          |    priority: 2
          |    pattern: |
          |      trigger = bears
          |      arg: Gummy = >amod
       """.stripMargin

    val extractors = eeGummy.compileRuleString(rules)
    val mentions = eeGummy.extractMentions(extractors).toArray

    // "gummy" from first rule and the main event with both args in second
    mentions should have size (4)

    getMentionsWithLabel(mentions, "GummyBear") should have size (0)

  }

}
