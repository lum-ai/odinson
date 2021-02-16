package ai.lum.odinson.state

import ai.lum.odinson.StateMatch
import ai.lum.odinson.utils.TestUtils.OdinsonTest

class TestState extends OdinsonTest {

  // Becky ate gummy bears
  val docGummy = getDocument("becky-gummy-bears-v2")
  val eeGummy = mkExtractorEngine(docGummy)

  behavior of "State"

  it should "not be used in extractNoState" in {
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
    val noStateMentions = eeGummy.extractNoState(extractors).toArray
    noStateMentions should have size (1)
    val first = getMentionsWithLabel(noStateMentions, "First")
    first should have size(1)

  }

  it should "support StateQueries in basic patterns" in {
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
    val mentions = eeGummy.extractMentions(extractors).toArray

    val first = getMentionsWithLabel(mentions, "First")
    first should have size(1)

    val second = getMentionsWithLabel(mentions, "Second")
    second should have size(1)

    val third = getMentionsWithLabel(mentions, "Third")
    third should have size(1)

    val fourth = getMentionsWithLabel(mentions, "Fourth")
    fourth should have size(1)

    // the four main mentions and the promoted arg
    mentions should have size(5)
    eeGummy.clearState()
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
    val mentions = eeGummy.extractMentions(extractors).toArray

    // First event -- make sure it's there
    val first = getMentionsWithLabel(mentions, "First")
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
    val second = getMentionsWithLabel(mentions, "Second")
    second should have size(1)
    val secondMention = second.head
    // There should be one argument, for the whatDid
    secondMention.arguments should have size(1)
    val didMentions = secondMention.arguments("whatDid")
    didMentions should have size(1)
    // And that argument should consist of "ate"
    val did = didMentions.head
    eeGummy.getStringForSpan(did.luceneDocId, did.odinsonMatch) should be("ate")

    // Overall, there should be four mentions found, the two main mentions and the promoted args
    mentions should have size(4)

    eeGummy.clearState()
    eeGummy.close()


  }

  it should "promote Args even if one of several was already in the state" in {
    val rules =
      """
        |rules:
        |  - name: first
        |    label: First
        |    type: basic
        |    priority: 1
        |    pattern: |
        |      Becky
        |
        |  - name: second
        |    label: Second
        |    type: event
        |    priority: 2
        |    pattern: |
        |      trigger = ate
        |      arg: ^First+ = >> []
        |""".stripMargin

    // Becky ate gummy bears.
    val extractors = eeGummy.compileRuleString(rules)
    val mentions = eeGummy.extractMentions(extractors).toArray

    val first = getMentionsWithLabel(mentions, "First")
    // the original "Becky", then the two found args ("bears" and ".")
    // since they were promoted
    first should have size(3)

    val second = getMentionsWithLabel(mentions, "Second")
    // one mention (the event)
    second should have size(1)
    val secondMention = second.head
    // should have been converted to a statematch
    secondMention.odinsonMatch shouldBe a [StateMatch]

    // The args should all have been converted and promoted
    val args = secondMention.arguments("arg")
    args should have size(3)

    args foreach { arg =>
      arg.odinsonMatch shouldBe a [StateMatch]
    }
    eeGummy.clearState()

  }

  it should "promote but not convert if not using the state" in {
    val rules =
      """
        |rules:
        |  - name: second
        |    label: Second
        |    type: event
        |    priority: 2
        |    pattern: |
        |      trigger = ate
        |      arg: ^First+ = >> []
        |""".stripMargin

    // Becky ate gummy bears.
    val extractors = eeGummy.compileRuleString(rules)
    val mentions = eeGummy.extractNoState(extractors).toArray

    val first = getMentionsWithLabel(mentions, "First")
    // the original "Becky", then the two found args ("bears" and ".")
    // since they were promoted
    first should have size(3)
    val firstMention = first.head
    // This match should have been converted to a StateMatch
    firstMention.odinsonMatch should not be a [StateMatch]

    val second = getMentionsWithLabel(mentions, "Second")
    // one mention (the event)
    second should have size(1)
    val secondMention = second.head
    // should not have been converted to a statematch
    secondMention.odinsonMatch should not be a [StateMatch]

    // The args should all have been converted and promoted
    val args = secondMention.arguments("arg")
    args should have size(3)

    args foreach { arg =>
      arg.odinsonMatch should not be a [StateMatch]
      arg.label.get should be("First")
    }
    eeGummy.clearState()
  }


}
