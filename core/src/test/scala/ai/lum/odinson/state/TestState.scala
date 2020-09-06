package ai.lum.odinson.state

import ai.lum.odinson.BaseSpec
import ai.lum.odinson.events.EventSpec

class TestState extends EventSpec {

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

  it should "add promoted arguments to the state to be able to retrieve them" in {
    val rules =
      """
        |rules:
        |  - name: first
        |    label: First
        |    type: event
        |    priority: 1
        |    pattern: |
        |      trigger = ate
        |      person:^Person = >nsubj []
        |
        |  - name: second
        |    label: Second
        |    type: event
        |    priority: 2
        |    pattern: |
        |      trigger = @Person
        |      whatDid = <nsubj []
        |""".stripMargin

    val extractors = eeGummy.compileRuleString(rules)
    val mentions = eeGummy.extractMentions(extractors)



    // First event -- make sure it's there
    val first = mentions.filter(_.label.get == "First")
    first should have size(1)
    val firstMention = first.head
    // There should be one argument, for the person
    firstMention.arguments should have size(1)
    val personMentions = firstMention.arguments("person")
    personMentions should have size(1)
    // And that argument should be a Mention with label `Person`
    val person = personMentions.head
    person.label.getOrElse("NONE") should be("Person")

    // Second event, which relies on the promoted argument should be found
    val second = mentions.filter(_.label.get == "Second")
    second should have size(1)
    val secondMention = second.head
    // There should be one argument, for the whatDid
    secondMention.arguments should have size(1)
    val didMentions = firstMention.arguments("whatDid")
    didMentions should have size(1)
    // And that argument should consist of "ate"
    val did = didMentions.head
    eeGummy.getStringForSpan(did.luceneDocId, did.odinsonMatch) should be("ate")

    // Overall, there should be two mentions found
    mentions should have size(2)
  }


}
