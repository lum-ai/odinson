package ai.lum.odinson.events

import ai.lum.odinson.test.utils.OdinsonTest

class TestEventsWithState extends OdinsonTest {

  "Odinson" should "match events referencing existing mentions when a in-memory state is used" in {
    val ee = extractorEngineWithSpecificState(getDocument("step-bros"), "memory")

    val grammar = """
    rules:
      - name: person-rule
        type: basic
        priority: 1
        label: Person
        pattern: |
          [tag=NNP]{3}

      - name: state-based-rule
        type: event
        label: EventBasedPersonRule
        priority: 2
        pattern: |
          trigger = [lemma=play]
          # NOTE: ending with @Person won't work
          arg: Person = >nsubj
    """

    val extractors = ee.compileRuleString(grammar).toVector

    val mentions = ee.extractMentions(
      extractors = extractors, 
      allowTriggerOverlaps = false,
      disableMatchSelector = false
    ).toVector
    
    // mentions.foreach{ m => println(s"${m.label.get}\t${m.foundBy}\t(${m.start}, ${m.end})")}
    getMentionsWithLabel(mentions, "EventBasedPersonRule") should have size (1)
  }

  it should "not match events referencing existing mentions when no state is used" in {
    val ee = extractorEngineWithSpecificState(getDocument("step-bros"), "mock")

    val grammar = """
    rules:
      - name: person-rule
        type: basic
        priority: 1
        label: Person
        pattern: |
          [tag=NNP]{3}

      - name: state-based-rule
        type: event
        label: EventBasedPersonRule
        priority: 2
        pattern: |
          trigger = [lemma=play]
          # NOTE: ending with @Person won't work
          arg: Person = >nsubj
    """

    val extractors = ee.compileRuleString(grammar).toVector

    val mentions = ee.extractMentions(
      extractors = extractors, 
      allowTriggerOverlaps = false,
      disableMatchSelector = false
    ).toVector
    
    getMentionsWithLabel(mentions, "EventBasedPersonRule") should have size (0)
  }

  it should "match events referencing existing mentions when a in-memory state is used along with a metadataFilter" in {
    val ee = extractorEngineWithSpecificState(getDocument("step-bros"), "memory")

    val grammar = """
    metadataFilters: doc_id == 'step-bros'

    rules:
      - name: person-rule
        type: basic
        priority: 1
        label: Person
        pattern: |
          [tag=NNP]{3}

      - name: state-based-rule
        type: event
        label: EventBasedPersonRule
        priority: 2
        pattern: |
          trigger = [lemma=play]
          # NOTE: ending with @Person won't work
          arg: Person = >nsubj
    """

    val extractors = ee.compileRuleString(grammar).toVector

    val mentions = ee.extractMentions(
      extractors = extractors, 
      allowTriggerOverlaps = false,
      disableMatchSelector = false
    ).toVector

    getMentionsWithLabel(mentions, "EventBasedPersonRule") should have size (1)
  }

  it should "not match events referencing existing mentions when no in-memory state is used (metadataQuery invariant)" in {
    val ee = extractorEngineWithSpecificState(getDocument("step-bros"), "memory")

    val grammar = """
    metadataFilters: doc_id == 'step-bros'

    rules:
      - name: person-rule
        type: basic
        priority: 1
        label: Person
        pattern: |
          [tag=NNP]{3}

      - name: state-based-rule
        type: event
        label: EventBasedPersonRule
        priority: 2
        pattern: |
          trigger = [lemma=play]
          # NOTE: ending with @Person won't work
          arg: Person = >nsubj
    """

    val extractors = ee.compileRuleString(grammar).toVector

    val mentions = ee.extractMentions(
      extractors = extractors, 
      allowTriggerOverlaps = false,
      disableMatchSelector = false
    ).toVector

    getMentionsWithLabel(mentions, "EventBasedPersonRule") should have size (1)
  }
}