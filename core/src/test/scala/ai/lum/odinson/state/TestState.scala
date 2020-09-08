package ai.lum.odinson.state

import ai.lum.odinson.BaseSpec

class TestState extends BaseSpec{

  // Becky ate gummy bears
  val docGummy = getDocument("becky-gummy-bears-v2")
  val eeGummy = Utils.mkExtractorEngine(docGummy)

  "State" should "support StateQueries in basic patterns" in {

    val rules = """
                  |rules:
                  |  - name: first
                  |    label: First
                  |    type: basic
                  |    priority: 1
                  |    pattern: |
                  |      ate
                  |
                  |  - name: second
                  |    label: Second
                  |    type: event
                  |    priority: 2
                  |    pattern: |
                  |      trigger = @First
                  |      theme = >dobj []
                  |
                  |  - name: third
                  |    label: Third
                  |    type: basic
                  |    priority: 2
                  |    pattern: |
                  |      @First >dobj []
                  |
                  |  - name: fourth
                  |    label: Fourth
                  |    type: basic
                  |    priority: 2
                  |    pattern: |
                  |      [] <dobj @First
       """.stripMargin

    val extractors = eeGummy.ruleReader.compileRuleString(rules)
    val mentions = eeGummy.extractMentions(extractors)

    val first = mentions.filter(_.label.get == "First")
    first should have size(1)

    val second = mentions.filter(_.label.get == "Second")
    second should have size(1)

    val third = mentions.filter(_.label.get == "Third")
    third should have size(1)

    val fourth = mentions.filter(_.label.get == "Fourth")
    fourth should have size(1)

    mentions should have size(4)

  }

}
