package ai.lum.odinson.events

import ai.lum.odinson.test.utils.OdinsonTest

class TestProblemGrammar extends OdinsonTest {

  "Odinson" should "match previously found multi-token mentions referenced in an event arg" in {
    val ee = mkExtractorEngine("step-bros")

    val grammar1 = """
    rules:
      - name: person-rule
        type: basic
        priority: 1
        label: Person
        pattern: |
          [tag=NNP]{3}

      - name: problem-rule
        type: event
        label: ConstrainedRule
        priority: 2
        pattern: |
          trigger = [lemma=play]
          # NOTE: this *should* match, but currently doesn't
          arg: Person = >nsubj @Person
    """

    val extractors = ee.compileRuleString(grammar1).toVector

    val mentions = ee.extractMentions(
      extractors = extractors, 
      allowTriggerOverlaps = false,
      disableMatchSelector = false
    ).toVector

    val res = mentions.getMentionsWithLabel("ConstrainedRule")
    res should have size 1
  }

  it should "match previously found multi-token mentions referenced in an event arg when a metadataFilter is used" in {
    val ee = ExtractorEngine("step-bros")

    val grammar1 = """
    metadataFilters: doc_id == 'step-bros'

    rules:
      - name: person-rule
        type: basic
        priority: 1
        label: Person
        pattern: |
          [tag=NNP]{3}

      - name: problem-rule
        type: event
        label: ConstrainedRule
        priority: 2
        pattern: |
          trigger = [lemma=play]
          # NOTE: this *should* match, but currently doesn't
          arg: Person = >nsubj @Person
    """

    val extractors = ee.compileRuleString(grammar1).toVector

    val mentions = ee.extractMentions(
      extractors = extractors, 
      allowTriggerOverlaps = false,
      disableMatchSelector = false
    ).toVector

    val res = mentions.getMentionsWithLabel("ConstrainedRule")
    res should have size 1
  }
}