package ai.lum.odinson.events

import org.scalatest._

import ai.lum.odinson.{Document, EventMatch, OdinsonMatch}
import ai.lum.odinson.BaseSpec

class TestEventTriggers extends EventSpec {
  "Odinson" should "match events for all trigger mentions using a basic pattern" in {
    val json = getJsonDocument("4")
    val doc = getDocumentFromJson(json)
    val ee = Utils.mkExtractorEngine(doc)
    val rules = """
      |vars:
      |  result: ([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)
      |rules:
      |  - name: example-rule
      |    type: basic
      |    pattern: |
      |      animals >nmod_such_as >/conj.*/? (?<result>${result})
      |""".stripMargin
    val extractors = ee.ruleReader.compileRuleFile(rules)
    val mentions = ee.extractMentions(extractors)
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    val expectedResults = List("hedgehogs", "coypu", "wild cloven-footed animals", "deer", "zoo animals")
    animals should contain theSameElementsInOrderAs expectedResults
  }

  it should "match events for all trigger mentions using an event pattern" in {
    val json = getJsonDocument("4")
    val doc = getDocumentFromJson(json)
    val ee = Utils.mkExtractorEngine(doc)
    val rules = """
      |vars:
      |  result: ([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)
      |rules:
      |  - name: example-rule
      |    type: event
      |    pattern: |
      |      trigger = animals
      |      result = >nmod_such_as >/conj.*/? ${result}
      |""".stripMargin
    val extractors = ee.ruleReader.compileRuleFile(rules)
    val mentions = ee.extractMentions(extractors)
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    val expectedResults = List("hedgehogs", "coypu", "wild cloven-footed animals", "deer", "zoo animals")
    animals should contain theSameElementsInOrderAs expectedResults
  }

  it should "match events for all trigger mentions using an event pattern with quantifiers in the trigger" in {
    val json = getJsonDocument("4")
    val doc = getDocumentFromJson(json)
    val ee = Utils.mkExtractorEngine(doc)
    val rules = """
      |vars:
      |  result: ([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)
      |rules:
      |  - name: example-rule
      |    type: event
      |    pattern: |
      |      trigger = wild? animals
      |      result = >nmod_such_as >/conj.*/? ${result}
      |""".stripMargin
    val extractors = ee.ruleReader.compileRuleFile(rules)
    val mentions = ee.extractMentions(extractors)
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    val expectedResults = List("hedgehogs", "coypu", "wild cloven-footed animals", "deer", "zoo animals")
    animals should contain theSameElementsInOrderAs expectedResults
  }

  it should "match events for all trigger mentions using an event pattern with quantifiers in the trigger (variable right hand side)" in {
    val json = getJsonDocument("4")
    val doc = getDocumentFromJson(json)
    val ee = Utils.mkExtractorEngine(doc)
    val rules = """
      |vars:
      |  result: ([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)
      |rules:
      |  - name: example-rule
      |    type: event
      |    pattern: |
      |      trigger = [tag=JJ]* ([tag=NNS] [tag=JJ])?
      |      result = >nmod_such_as >/conj.*/? ${result}
      |""".stripMargin
    val extractors = ee.ruleReader.compileRuleFile(rules)
    val mentions = ee.extractMentions(extractors)
    val triggers = mentions.map(m => ee.getString(m.luceneDocId, m.odinsonMatch.asInstanceOf[EventMatch].trigger))
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    val expectedTriggers = List("wild animals such", "wild animals such", "wild animals such", "wild cloven-footed animals such", "wild cloven-footed animals such")
    val expectedResults = List("hedgehogs", "coypu", "wild cloven-footed animals", "deer", "zoo animals")
    triggers should contain theSameElementsInOrderAs expectedTriggers
    animals should contain theSameElementsInOrderAs expectedResults
  }

  it should "match events for all trigger mentions using an event pattern with quantifiers in the trigger (overlap with different start and end)" in {
    val json = getJsonDocument("4")
    val doc = getDocumentFromJson(json)
    val ee = Utils.mkExtractorEngine(doc)
    val rules = """
      |vars:
      |  result: ([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)
      |rules:
      |  - name: example-rule
      |    type: event
      |    pattern: |
      |      trigger = [tag=DT | tag=JJ] [tag=JJ]
      |      result = <amod [lemma=animal]
      |""".stripMargin
    val extractors = ee.ruleReader.compileRuleFile(rules)
    val mentions = ee.extractMentions(extractors)
    val triggers = mentions.map(m => ee.getString(m.luceneDocId, m.odinsonMatch.asInstanceOf[EventMatch].trigger))
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    val expectedTriggers = List("Some wild", "any wild")
    val expectedResults = List("animals", "animals")
    triggers should contain theSameElementsInOrderAs expectedTriggers
    animals should contain theSameElementsInOrderAs expectedResults
  }

  it should "match events for all trigger mentions using an event pattern with quantifiers in the trigger (greedy)" in {
    val json = getJsonDocument("4")
    val doc = getDocumentFromJson(json)
    val ee = Utils.mkExtractorEngine(doc)
    val rules = """
      |vars:
      |  result: ([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)
      |rules:
      |  - name: example-rule
      |    type: event
      |    pattern: |
      |      trigger = some []* animals
      |      result = (>nmod_such_as | >nmod_including) >/conj.*/? ${result}
      |""".stripMargin
    val extractors = ee.ruleReader.compileRuleFile(rules)
    val mentions = ee.extractMentions(extractors)
    val triggers = mentions.map(m => ee.getString(m.luceneDocId, m.odinsonMatch.asInstanceOf[EventMatch].trigger))
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    val expectedTriggers = List("Some wild animals such as hedgehogs , coypu , and any wild cloven-footed animals such as deer and zoo animals")
    val expectedResults = List("elephants")
    triggers should contain theSameElementsInOrderAs expectedTriggers
    animals should contain theSameElementsInOrderAs expectedResults
  }

  it should "match events for all trigger mentions using an event pattern with quantifiers in the trigger (greedy; allow trigger overlaps)" in {
    val json = getJsonDocument("4")
    val doc = getDocumentFromJson(json)
    val ee = Utils.mkExtractorEngine(doc)
    val rules = """
      |vars:
      |  result: ([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)
      |rules:
      |  - name: example-rule
      |    type: event
      |    pattern: |
      |      trigger = some []* animals
      |      result = (>nmod_such_as | >nmod_including) >/conj.*/? ${result}
      |""".stripMargin
    val extractors = ee.ruleReader.compileRuleFile(rules)
    val mentions = ee.extractMentions(extractors, allowTriggerOverlaps = true)
    val triggers = mentions.map(m => ee.getString(m.luceneDocId, m.odinsonMatch.asInstanceOf[EventMatch].trigger))
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    // TODO: fix this  
    val expectedTriggers = List("Some wild animals such as hedgehogs , coypu , and any wild cloven-footed animals such as deer and zoo animals",
                                "Some wild animals such as hedgehogs , coypu , and any wild cloven-footed animals such as deer and zoo animals",
                                "Some wild animals such as hedgehogs , coypu , and any wild cloven-footed animals such as deer and zoo animals",
                                "Some wild animals such as hedgehogs , coypu , and any wild cloven-footed animals such as deer and zoo animals",
                                "Some wild animals such as hedgehogs , coypu , and any wild cloven-footed animals such as deer and zoo animals",
                                "Some wild animals such as hedgehogs , coypu , and any wild cloven-footed animals such as deer and zoo animals")
    val expectedResults = List("hedgehogs", "coypu", "wild cloven-footed animals", "deer", "zoo animals", "elephants")
    triggers should contain theSameElementsInOrderAs expectedTriggers
    animals should contain theSameElementsInOrderAs expectedResults
  }

  it should "match events for all trigger mentions using an event pattern with quantifiers in the trigger (laziness)" in {
    val json = getJsonDocument("4")
    val doc = getDocumentFromJson(json)
    val ee = Utils.mkExtractorEngine(doc)
    val rules = """
      |vars:
      |  result: ([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)
      |rules:
      |  - name: example-rule
      |    type: event
      |    pattern: |
      |      trigger = some []*? animals
      |      result = >nmod_such_as >/conj.*/? ${result}
      |""".stripMargin
    val extractors = ee.ruleReader.compileRuleFile(rules)
    val mentions = ee.extractMentions(extractors)
    val triggers = mentions.map(m => ee.getString(m.luceneDocId, m.odinsonMatch.asInstanceOf[EventMatch].trigger))
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    val expectedTriggers = List("Some wild animals", "Some wild animals", "Some wild animals")
    val expectedResults = List("hedgehogs", "coypu", "wild cloven-footed animals")
    triggers should contain theSameElementsInOrderAs expectedTriggers
    animals should contain theSameElementsInOrderAs expectedResults
  }

  it should "match arguments of correct length using a basic pattern" in {
    val json = getJsonDocument("7")
    val doc = getDocumentFromJson(json)
    val ee = Utils.mkExtractorEngine(doc)
    
    val rules = """
      |vars:
      |  result: ([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)
      |rules:
      |  - name: example-rule
      |    type: basic
      |    pattern: |
      |      animals >nmod_such_as >/conj.*/? (?<result>${result})
      |""".stripMargin
    val extractors = ee.ruleReader.compileRuleFile(rules)
    val mentions = ee.extractMentions(extractors)
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    val expectedResults = List("rabbit", "possum", "quail", "badger", "iguana", "armadillo", "variety of river fish")
    animals should contain theSameElementsInOrderAs expectedResults
  }

  it should "match arguments of correct length using an event pattern" in {
    val json = getJsonDocument("7")
    val doc = getDocumentFromJson(json)
    val ee = Utils.mkExtractorEngine(doc)
    val rules = """
      |vars:
      |  result: ([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)
      |rules:
      |  - name: example-rule
      |    type: event
      |    pattern: |
      |      trigger = animals
      |      result = >nmod_such_as >/conj.*/? ${result}
      |""".stripMargin
    val extractors = ee.ruleReader.compileRuleFile(rules)
    val mentions = ee.extractMentions(extractors)
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    val expectedResults = List("rabbit", "possum", "quail", "badger", "iguana", "armadillo", "variety of river fish")
    animals should contain theSameElementsInOrderAs expectedResults
  }

}
