package ai.lum.odinson.state

import ai.lum.odinson.{BaseSpec, StateMatch}
import ai.lum.odinson.events.EventSpec

class TestState extends EventSpec {

  // Becky ate gummy bears
  val docGummy = getDocument("becky-gummy-bears-v2")
  val eeGummy = Utils.mkExtractorEngine(docGummy)

  behavior of "State"

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

    val first = mentions.filter(_.label.getOrElse("None") == "First")
    first should have size(1)

    val second = mentions.filter(_.label.getOrElse("None") == "Second")
    second should have size(1)

    val third = mentions.filter(_.label.getOrElse("None") == "Third")
    third should have size(1)

    val fourth = mentions.filter(_.label.getOrElse("None") == "Fourth")
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
    val first = mentions.filter(_.label.getOrElse("None") == "First")
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
    val second = mentions.filter(_.label.getOrElse("None") == "Second")
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

    val first = mentions.filter(_.label.getOrElse("None") == "First")
    // the original "Becky", then the two found args ("bears" and ".")
    // since they were promoted
    first should have size(3)

    val second = mentions.filter(_.label.getOrElse("None") == "Second")
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
//      first should contain(arg)
    }
    eeGummy.clearState()

    val noStateMentions = eeGummy.extractNoState(extractors)


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

    val first = mentions.filter(_.label.getOrElse("None") == "First")
    // the original "Becky", then the two found args ("bears" and ".")
    // since they were promoted
    first should have size(3)
    val firstMention = first.head
    // This match should have been converted to a StateMatch
    firstMention.odinsonMatch should not be a [StateMatch]

    val second = mentions.filter(_.label.getOrElse("None") == "Second")
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
//      first should contain(arg)
    }
    eeGummy.clearState()
  }

//  it should "properly convert nested events to StateMatch" in {
//
//    eeGummy.clearState()
//  }


}
