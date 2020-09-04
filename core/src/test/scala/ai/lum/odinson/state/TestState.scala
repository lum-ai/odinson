package ai.lum.odinson.state

import ai.lum.odinson.BaseSpec

class TestState extends BaseSpec{

  // Becky ate gummy bears
  val docGummy = getDocument("becky-gummy-bears-v2")
  val eeGummy = Utils.mkExtractorEngine(docGummy)

  "SQLState" should "support StateQueries in basic patterns" in {

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
       """.stripMargin

    val extractors = eeGummy.ruleReader.compileRuleString(rules)
    val mentions = eeGummy.extractMentions(extractors)

    val first = mentions.filter(_.label.get == "First")
    first should have size(1)

    // memory state fails here (because it's not fully implemented)
    // SQL state succeeds here
    val second = mentions.filter(_.label.get == "Second")
    second should have size(1)

    // the sql state succeeds on second, but fails here
    val third = mentions.filter(_.label.get == "Third")
    third should have size(1)

    mentions should have size(3)

  }

}
