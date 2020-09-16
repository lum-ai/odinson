package ai.lum.odinson.state

import ai.lum.odinson.BaseSpec

class TestMockState extends BaseSpec {

  val docGummy = getDocument("becky-gummy-bears-v2")

  val eeGummy = Utils.extractorEngineWithSpecificState(docGummy, "mock")
  val eeGummyMemory = Utils.extractorEngineWithSpecificState(docGummy, "memory")

  "MockState" should "return mentions" in {
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

    val extractors = eeGummy.ruleReader.compileRuleString(rules)
    val mentions = eeGummy.extractMentions(extractors).toArray

    mentions should have size (2)

    mentions.filter(_.label.get == "Gummy") should have size (0)


  }

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

    mentions should have size (3)

    mentions.filter(_.label.get == "Gummy") should have size (1)

  }

}
