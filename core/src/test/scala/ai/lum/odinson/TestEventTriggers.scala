package ai.lum.odinson

import org.scalatest._

class TestEventTriggers extends FlatSpec with Matchers {

  import TestEvents._

  "Odinson" should "match events for all trigger mentions using a basic pattern" in {
    val json = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":27,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Some","wild","animals","such","as","hedgehogs",",","coypu",",","and","any","wild","cloven-footed","animals","such","as","deer","and","zoo","animals","including","elephants","can","also","contract","it","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Some","wild","animals","such","as","hedgehogs",",","coypu",",","and","any","wild","cloven-footed","animals","such","as","deer","and","zoo","animals","including","elephants","can","also","contract","it","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["DT","JJ","NNS","JJ","IN","NNS",",","NN",",","CC","DT","JJ","JJ","NNS","JJ","IN","NNS","CC","NN","NNS","VBG","NNS","MD","RB","VB","PRP","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["some","wild","animal","such","as","hedgehog",",","coypu",",","and","any","wild","cloven-footed","animal","such","as","deer","and","zoo","animal","include","elephant","can","also","contract","it","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","I-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","O","O","B-NP","I-NP","I-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","I-NP","B-PP","B-NP","B-VP","I-VP","I-VP","B-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","roots":[24],"edges":[[2,0,"det"],[2,1,"amod"],[2,5,"nmod_such_as"],[2,7,"nmod_such_as"],[2,13,"nmod_such_as"],[3,4,"mwe"],[5,3,"case"],[5,6,"punct"],[5,7,"conj_and"],[5,8,"punct"],[5,9,"cc"],[5,13,"conj_and"],[13,10,"det"],[13,11,"amod"],[13,12,"amod"],[13,16,"nmod_such_as"],[13,19,"nmod_such_as"],[14,15,"mwe"],[16,14,"case"],[16,17,"cc"],[16,19,"conj_and"],[16,21,"nmod_including"],[19,18,"compound"],[21,20,"case"],[24,2,"nsubj"],[24,22,"aux"],[24,23,"advmod"],[24,25,"dobj"],[24,26,"punct"]]}]}]}"""
    val doc = Document.fromJson(json)
    val ee = TestUtils.mkExtractorEngine(doc)
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
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head))
    val expectedResults = List("hedgehogs", "coypu", "wild cloven-footed animals", "deer", "zoo animals")
    animals should contain theSameElementsInOrderAs expectedResults
  }

  it should "match events for all trigger mentions using an event pattern" in {
    val json = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":27,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Some","wild","animals","such","as","hedgehogs",",","coypu",",","and","any","wild","cloven-footed","animals","such","as","deer","and","zoo","animals","including","elephants","can","also","contract","it","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Some","wild","animals","such","as","hedgehogs",",","coypu",",","and","any","wild","cloven-footed","animals","such","as","deer","and","zoo","animals","including","elephants","can","also","contract","it","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["DT","JJ","NNS","JJ","IN","NNS",",","NN",",","CC","DT","JJ","JJ","NNS","JJ","IN","NNS","CC","NN","NNS","VBG","NNS","MD","RB","VB","PRP","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["some","wild","animal","such","as","hedgehog",",","coypu",",","and","any","wild","cloven-footed","animal","such","as","deer","and","zoo","animal","include","elephant","can","also","contract","it","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","I-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","O","O","B-NP","I-NP","I-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","I-NP","B-PP","B-NP","B-VP","I-VP","I-VP","B-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","roots":[24],"edges":[[2,0,"det"],[2,1,"amod"],[2,5,"nmod_such_as"],[2,7,"nmod_such_as"],[2,13,"nmod_such_as"],[3,4,"mwe"],[5,3,"case"],[5,6,"punct"],[5,7,"conj_and"],[5,8,"punct"],[5,9,"cc"],[5,13,"conj_and"],[13,10,"det"],[13,11,"amod"],[13,12,"amod"],[13,16,"nmod_such_as"],[13,19,"nmod_such_as"],[14,15,"mwe"],[16,14,"case"],[16,17,"cc"],[16,19,"conj_and"],[16,21,"nmod_including"],[19,18,"compound"],[21,20,"case"],[24,2,"nsubj"],[24,22,"aux"],[24,23,"advmod"],[24,25,"dobj"],[24,26,"punct"]]}]}]}"""
    val doc = Document.fromJson(json)
    val ee = TestUtils.mkExtractorEngine(doc)
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
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head))
    val expectedResults = List("hedgehogs", "coypu", "wild cloven-footed animals", "deer", "zoo animals")
    animals should contain theSameElementsInOrderAs expectedResults
  }

  it should "match events for all trigger mentions using an event pattern with quantifiers in the trigger" in {
    val json = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":27,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Some","wild","animals","such","as","hedgehogs",",","coypu",",","and","any","wild","cloven-footed","animals","such","as","deer","and","zoo","animals","including","elephants","can","also","contract","it","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Some","wild","animals","such","as","hedgehogs",",","coypu",",","and","any","wild","cloven-footed","animals","such","as","deer","and","zoo","animals","including","elephants","can","also","contract","it","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["DT","JJ","NNS","JJ","IN","NNS",",","NN",",","CC","DT","JJ","JJ","NNS","JJ","IN","NNS","CC","NN","NNS","VBG","NNS","MD","RB","VB","PRP","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["some","wild","animal","such","as","hedgehog",",","coypu",",","and","any","wild","cloven-footed","animal","such","as","deer","and","zoo","animal","include","elephant","can","also","contract","it","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","I-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","O","O","B-NP","I-NP","I-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","I-NP","B-PP","B-NP","B-VP","I-VP","I-VP","B-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","roots":[24],"edges":[[2,0,"det"],[2,1,"amod"],[2,5,"nmod_such_as"],[2,7,"nmod_such_as"],[2,13,"nmod_such_as"],[3,4,"mwe"],[5,3,"case"],[5,6,"punct"],[5,7,"conj_and"],[5,8,"punct"],[5,9,"cc"],[5,13,"conj_and"],[13,10,"det"],[13,11,"amod"],[13,12,"amod"],[13,16,"nmod_such_as"],[13,19,"nmod_such_as"],[14,15,"mwe"],[16,14,"case"],[16,17,"cc"],[16,19,"conj_and"],[16,21,"nmod_including"],[19,18,"compound"],[21,20,"case"],[24,2,"nsubj"],[24,22,"aux"],[24,23,"advmod"],[24,25,"dobj"],[24,26,"punct"]]}]}]}"""
    val doc = Document.fromJson(json)
    val ee = TestUtils.mkExtractorEngine(doc)
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
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head))
    val expectedResults = List("hedgehogs", "coypu", "wild cloven-footed animals", "deer", "zoo animals")
    animals should contain theSameElementsInOrderAs expectedResults
  }

  it should "match events for all trigger mentions using an event pattern with quantifiers in the trigger (variable right hand side)" in {
    val json = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":27,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Some","wild","animals","such","as","hedgehogs",",","coypu",",","and","any","wild","cloven-footed","animals","such","as","deer","and","zoo","animals","including","elephants","can","also","contract","it","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Some","wild","animals","such","as","hedgehogs",",","coypu",",","and","any","wild","cloven-footed","animals","such","as","deer","and","zoo","animals","including","elephants","can","also","contract","it","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["DT","JJ","NNS","JJ","IN","NNS",",","NN",",","CC","DT","JJ","JJ","NNS","JJ","IN","NNS","CC","NN","NNS","VBG","NNS","MD","RB","VB","PRP","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["some","wild","animal","such","as","hedgehog",",","coypu",",","and","any","wild","cloven-footed","animal","such","as","deer","and","zoo","animal","include","elephant","can","also","contract","it","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","I-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","O","O","B-NP","I-NP","I-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","I-NP","B-PP","B-NP","B-VP","I-VP","I-VP","B-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","roots":[24],"edges":[[2,0,"det"],[2,1,"amod"],[2,5,"nmod_such_as"],[2,7,"nmod_such_as"],[2,13,"nmod_such_as"],[3,4,"mwe"],[5,3,"case"],[5,6,"punct"],[5,7,"conj_and"],[5,8,"punct"],[5,9,"cc"],[5,13,"conj_and"],[13,10,"det"],[13,11,"amod"],[13,12,"amod"],[13,16,"nmod_such_as"],[13,19,"nmod_such_as"],[14,15,"mwe"],[16,14,"case"],[16,17,"cc"],[16,19,"conj_and"],[16,21,"nmod_including"],[19,18,"compound"],[21,20,"case"],[24,2,"nsubj"],[24,22,"aux"],[24,23,"advmod"],[24,25,"dobj"],[24,26,"punct"]]}]}]}"""
    val doc = Document.fromJson(json)
    val ee = TestUtils.mkExtractorEngine(doc)
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
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head))
    val expectedTriggers = List("wild animals such", "wild animals such", "wild animals such", "wild cloven-footed animals such", "wild cloven-footed animals such")
    val expectedResults = List("hedgehogs", "coypu", "wild cloven-footed animals", "deer", "zoo animals")
    triggers should contain theSameElementsInOrderAs expectedTriggers
    animals should contain theSameElementsInOrderAs expectedResults
  }

  it should "match events for all trigger mentions using an event pattern with quantifiers in the trigger (overlap with different start and end)" in {
    val json = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":27,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Some","wild","animals","such","as","hedgehogs",",","coypu",",","and","any","wild","cloven-footed","animals","such","as","deer","and","zoo","animals","including","elephants","can","also","contract","it","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Some","wild","animals","such","as","hedgehogs",",","coypu",",","and","any","wild","cloven-footed","animals","such","as","deer","and","zoo","animals","including","elephants","can","also","contract","it","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["DT","JJ","NNS","JJ","IN","NNS",",","NN",",","CC","DT","JJ","JJ","NNS","JJ","IN","NNS","CC","NN","NNS","VBG","NNS","MD","RB","VB","PRP","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["some","wild","animal","such","as","hedgehog",",","coypu",",","and","any","wild","cloven-footed","animal","such","as","deer","and","zoo","animal","include","elephant","can","also","contract","it","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","I-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","O","O","B-NP","I-NP","I-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","I-NP","B-PP","B-NP","B-VP","I-VP","I-VP","B-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","roots":[24],"edges":[[2,0,"det"],[2,1,"amod"],[2,5,"nmod_such_as"],[2,7,"nmod_such_as"],[2,13,"nmod_such_as"],[3,4,"mwe"],[5,3,"case"],[5,6,"punct"],[5,7,"conj_and"],[5,8,"punct"],[5,9,"cc"],[5,13,"conj_and"],[13,10,"det"],[13,11,"amod"],[13,12,"amod"],[13,16,"nmod_such_as"],[13,19,"nmod_such_as"],[14,15,"mwe"],[16,14,"case"],[16,17,"cc"],[16,19,"conj_and"],[16,21,"nmod_including"],[19,18,"compound"],[21,20,"case"],[24,2,"nsubj"],[24,22,"aux"],[24,23,"advmod"],[24,25,"dobj"],[24,26,"punct"]]}]}]}"""
    val doc = Document.fromJson(json)
    val ee = TestUtils.mkExtractorEngine(doc)
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
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head))
    val expectedTriggers = List("Some wild", "any wild")
    val expectedResults = List("animals", "animals")
    triggers should contain theSameElementsInOrderAs expectedTriggers
    animals should contain theSameElementsInOrderAs expectedResults
  }

  it should "match events for all trigger mentions using an event pattern with quantifiers in the trigger (greedy)" in {
    val json = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":27,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Some","wild","animals","such","as","hedgehogs",",","coypu",",","and","any","wild","cloven-footed","animals","such","as","deer","and","zoo","animals","including","elephants","can","also","contract","it","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Some","wild","animals","such","as","hedgehogs",",","coypu",",","and","any","wild","cloven-footed","animals","such","as","deer","and","zoo","animals","including","elephants","can","also","contract","it","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["DT","JJ","NNS","JJ","IN","NNS",",","NN",",","CC","DT","JJ","JJ","NNS","JJ","IN","NNS","CC","NN","NNS","VBG","NNS","MD","RB","VB","PRP","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["some","wild","animal","such","as","hedgehog",",","coypu",",","and","any","wild","cloven-footed","animal","such","as","deer","and","zoo","animal","include","elephant","can","also","contract","it","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","I-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","O","O","B-NP","I-NP","I-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","I-NP","B-PP","B-NP","B-VP","I-VP","I-VP","B-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","roots":[24],"edges":[[2,0,"det"],[2,1,"amod"],[2,5,"nmod_such_as"],[2,7,"nmod_such_as"],[2,13,"nmod_such_as"],[3,4,"mwe"],[5,3,"case"],[5,6,"punct"],[5,7,"conj_and"],[5,8,"punct"],[5,9,"cc"],[5,13,"conj_and"],[13,10,"det"],[13,11,"amod"],[13,12,"amod"],[13,16,"nmod_such_as"],[13,19,"nmod_such_as"],[14,15,"mwe"],[16,14,"case"],[16,17,"cc"],[16,19,"conj_and"],[16,21,"nmod_including"],[19,18,"compound"],[21,20,"case"],[24,2,"nsubj"],[24,22,"aux"],[24,23,"advmod"],[24,25,"dobj"],[24,26,"punct"]]}]}]}"""
    val doc = Document.fromJson(json)
    val ee = TestUtils.mkExtractorEngine(doc)
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
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head))
    val expectedTriggers = List("Some wild animals such as hedgehogs , coypu , and any wild cloven-footed animals such as deer and zoo animals")
    val expectedResults = List("elephants")
    triggers should contain theSameElementsInOrderAs expectedTriggers
    animals should contain theSameElementsInOrderAs expectedResults
  }

  it should "match events for all trigger mentions using an event pattern with quantifiers in the trigger (greedy; allow trigger overlaps)" in {
    val json = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":27,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Some","wild","animals","such","as","hedgehogs",",","coypu",",","and","any","wild","cloven-footed","animals","such","as","deer","and","zoo","animals","including","elephants","can","also","contract","it","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Some","wild","animals","such","as","hedgehogs",",","coypu",",","and","any","wild","cloven-footed","animals","such","as","deer","and","zoo","animals","including","elephants","can","also","contract","it","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["DT","JJ","NNS","JJ","IN","NNS",",","NN",",","CC","DT","JJ","JJ","NNS","JJ","IN","NNS","CC","NN","NNS","VBG","NNS","MD","RB","VB","PRP","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["some","wild","animal","such","as","hedgehog",",","coypu",",","and","any","wild","cloven-footed","animal","such","as","deer","and","zoo","animal","include","elephant","can","also","contract","it","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","I-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","O","O","B-NP","I-NP","I-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","I-NP","B-PP","B-NP","B-VP","I-VP","I-VP","B-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","roots":[24],"edges":[[2,0,"det"],[2,1,"amod"],[2,5,"nmod_such_as"],[2,7,"nmod_such_as"],[2,13,"nmod_such_as"],[3,4,"mwe"],[5,3,"case"],[5,6,"punct"],[5,7,"conj_and"],[5,8,"punct"],[5,9,"cc"],[5,13,"conj_and"],[13,10,"det"],[13,11,"amod"],[13,12,"amod"],[13,16,"nmod_such_as"],[13,19,"nmod_such_as"],[14,15,"mwe"],[16,14,"case"],[16,17,"cc"],[16,19,"conj_and"],[16,21,"nmod_including"],[19,18,"compound"],[21,20,"case"],[24,2,"nsubj"],[24,22,"aux"],[24,23,"advmod"],[24,25,"dobj"],[24,26,"punct"]]}]}]}"""
    val doc = Document.fromJson(json)
    val ee = TestUtils.mkExtractorEngine(doc)
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
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head))
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
    val json = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":27,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Some","wild","animals","such","as","hedgehogs",",","coypu",",","and","any","wild","cloven-footed","animals","such","as","deer","and","zoo","animals","including","elephants","can","also","contract","it","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Some","wild","animals","such","as","hedgehogs",",","coypu",",","and","any","wild","cloven-footed","animals","such","as","deer","and","zoo","animals","including","elephants","can","also","contract","it","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["DT","JJ","NNS","JJ","IN","NNS",",","NN",",","CC","DT","JJ","JJ","NNS","JJ","IN","NNS","CC","NN","NNS","VBG","NNS","MD","RB","VB","PRP","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["some","wild","animal","such","as","hedgehog",",","coypu",",","and","any","wild","cloven-footed","animal","such","as","deer","and","zoo","animal","include","elephant","can","also","contract","it","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","I-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","O","O","B-NP","I-NP","I-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","I-NP","B-PP","B-NP","B-VP","I-VP","I-VP","B-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","roots":[24],"edges":[[2,0,"det"],[2,1,"amod"],[2,5,"nmod_such_as"],[2,7,"nmod_such_as"],[2,13,"nmod_such_as"],[3,4,"mwe"],[5,3,"case"],[5,6,"punct"],[5,7,"conj_and"],[5,8,"punct"],[5,9,"cc"],[5,13,"conj_and"],[13,10,"det"],[13,11,"amod"],[13,12,"amod"],[13,16,"nmod_such_as"],[13,19,"nmod_such_as"],[14,15,"mwe"],[16,14,"case"],[16,17,"cc"],[16,19,"conj_and"],[16,21,"nmod_including"],[19,18,"compound"],[21,20,"case"],[24,2,"nsubj"],[24,22,"aux"],[24,23,"advmod"],[24,25,"dobj"],[24,26,"punct"]]}]}]}"""
    val doc = Document.fromJson(json)
    val ee = TestUtils.mkExtractorEngine(doc)
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
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head))
    val expectedTriggers = List("Some wild animals", "Some wild animals", "Some wild animals")
    val expectedResults = List("hedgehogs", "coypu", "wild cloven-footed animals")
    triggers should contain theSameElementsInOrderAs expectedTriggers
    animals should contain theSameElementsInOrderAs expectedResults
  }

  it should "match arguments of correct length using a basic pattern" in {
    val json = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":58,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Much","of","the","diet","in","these","communities","is","made","up","of","food","eaten","long","before","the","Europeans","arrived",":","verdolagas","(","purslane",")","and","other","wild","greens",",","herbs",",","wild","mushrooms","and","berries",",","and","small","animals","such","as","rabbit",",","possum",",","quail",",","badger",",","iguana",",","armadillo","and","a","variety","of","river","fish","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Much","of","the","diet","in","these","communities","is","made","up","of","food","eaten","long","before","the","Europeans","arrived",":","verdolagas","(","purslane",")","and","other","wild","greens",",","herbs",",","wild","mushrooms","and","berries",",","and","small","animals","such","as","rabbit",",","possum",",","quail",",","badger",",","iguana",",","armadillo","and","a","variety","of","river","fish","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["JJ","IN","DT","NN","IN","DT","NNS","VBZ","VBN","RP","IN","NN","VBD","RB","IN","DT","NNPS","VBD",":","NNS","-LRB-","NN","-RRB-","CC","JJ","JJ","NNS",",","NNS",",","JJ","NNS","CC","NNS",",","CC","JJ","NNS","JJ","IN","NN",",","NN",",","NN",",","NN",",","NN",",","NN","CC","DT","NN","IN","NN","NN","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["much","of","the","diet","in","these","community","be","make","up","of","food","eat","long","before","the","Europeans","arrive",":","verdolaga","(","purslane",")","and","other","wild","green",",","herb",",","wild","mushroom","and","berry",",","and","small","animal","such","as","rabbit",",","possum",",","quail",",","badger",",","iguana",",","armadillo","and","a","variety","of","river","fish","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","MISC","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-PP","B-NP","I-NP","B-PP","B-NP","I-NP","B-VP","I-VP","B-PRT","B-PP","B-NP","B-VP","B-ADVP","B-PP","B-NP","I-NP","B-VP","O","B-NP","I-NP","I-NP","I-NP","O","B-NP","I-NP","I-NP","I-NP","I-NP","I-NP","I-NP","I-NP","I-NP","I-NP","O","O","B-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","O","B-NP","O","B-NP","O","B-NP","O","B-NP","O","B-NP","I-NP","B-PP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","roots":[8],"edges":[[0,3,"nmod_of"],[3,1,"case"],[3,2,"det"],[3,6,"nmod_in"],[6,4,"case"],[6,5,"det"],[8,0,"nsubjpass"],[8,7,"auxpass"],[8,9,"compound:prt"],[8,11,"nmod_of"],[8,12,"xcomp"],[8,57,"punct"],[11,10,"case"],[12,17,"advcl_before"],[16,15,"det"],[17,13,"advmod"],[17,14,"mark"],[17,16,"nsubj"],[17,18,"punct"],[17,19,"dep"],[17,26,"dep"],[17,37,"dep"],[19,21,"appos"],[19,23,"cc"],[19,26,"conj_and"],[19,28,"conj_and"],[19,31,"conj_and"],[19,33,"conj_and"],[19,34,"punct"],[19,35,"cc"],[19,37,"conj_and"],[21,20,"punct"],[21,22,"punct"],[26,24,"amod"],[26,25,"amod"],[26,27,"punct"],[26,28,"conj_and"],[26,29,"punct"],[26,31,"conj_and"],[26,32,"cc"],[26,33,"conj_and"],[31,30,"amod"],[37,36,"amod"],[37,40,"nmod_such_as"],[37,42,"nmod_such_as"],[37,44,"nmod_such_as"],[37,46,"nmod_such_as"],[37,48,"nmod_such_as"],[37,50,"nmod_such_as"],[37,56,"nmod_such_as"],[38,39,"mwe"],[40,38,"case"],[40,41,"punct"],[40,42,"conj_and"],[40,43,"punct"],[40,44,"conj_and"],[40,45,"punct"],[40,46,"conj_and"],[40,47,"punct"],[40,48,"conj_and"],[40,49,"punct"],[40,50,"conj_and"],[40,51,"cc"],[40,56,"conj_and"],[52,53,"mwe"],[52,54,"mwe"],[56,52,"det:qmod"],[56,55,"compound"]]}]}]}"""
    val doc = Document.fromJson(json)
    val ee = TestUtils.mkExtractorEngine(doc)
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
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head))
    val expectedResults = List("rabbit", "possum", "quail", "badger", "iguana", "armadillo", "variety of river fish")
    animals should contain theSameElementsInOrderAs expectedResults
  }

  it should "match arguments of correct length using an event pattern" in {
    val json = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":58,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Much","of","the","diet","in","these","communities","is","made","up","of","food","eaten","long","before","the","Europeans","arrived",":","verdolagas","(","purslane",")","and","other","wild","greens",",","herbs",",","wild","mushrooms","and","berries",",","and","small","animals","such","as","rabbit",",","possum",",","quail",",","badger",",","iguana",",","armadillo","and","a","variety","of","river","fish","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Much","of","the","diet","in","these","communities","is","made","up","of","food","eaten","long","before","the","Europeans","arrived",":","verdolagas","(","purslane",")","and","other","wild","greens",",","herbs",",","wild","mushrooms","and","berries",",","and","small","animals","such","as","rabbit",",","possum",",","quail",",","badger",",","iguana",",","armadillo","and","a","variety","of","river","fish","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["JJ","IN","DT","NN","IN","DT","NNS","VBZ","VBN","RP","IN","NN","VBD","RB","IN","DT","NNPS","VBD",":","NNS","-LRB-","NN","-RRB-","CC","JJ","JJ","NNS",",","NNS",",","JJ","NNS","CC","NNS",",","CC","JJ","NNS","JJ","IN","NN",",","NN",",","NN",",","NN",",","NN",",","NN","CC","DT","NN","IN","NN","NN","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["much","of","the","diet","in","these","community","be","make","up","of","food","eat","long","before","the","Europeans","arrive",":","verdolaga","(","purslane",")","and","other","wild","green",",","herb",",","wild","mushroom","and","berry",",","and","small","animal","such","as","rabbit",",","possum",",","quail",",","badger",",","iguana",",","armadillo","and","a","variety","of","river","fish","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","MISC","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-PP","B-NP","I-NP","B-PP","B-NP","I-NP","B-VP","I-VP","B-PRT","B-PP","B-NP","B-VP","B-ADVP","B-PP","B-NP","I-NP","B-VP","O","B-NP","I-NP","I-NP","I-NP","O","B-NP","I-NP","I-NP","I-NP","I-NP","I-NP","I-NP","I-NP","I-NP","I-NP","O","O","B-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","O","B-NP","O","B-NP","O","B-NP","O","B-NP","O","B-NP","I-NP","B-PP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","roots":[8],"edges":[[0,3,"nmod_of"],[3,1,"case"],[3,2,"det"],[3,6,"nmod_in"],[6,4,"case"],[6,5,"det"],[8,0,"nsubjpass"],[8,7,"auxpass"],[8,9,"compound:prt"],[8,11,"nmod_of"],[8,12,"xcomp"],[8,57,"punct"],[11,10,"case"],[12,17,"advcl_before"],[16,15,"det"],[17,13,"advmod"],[17,14,"mark"],[17,16,"nsubj"],[17,18,"punct"],[17,19,"dep"],[17,26,"dep"],[17,37,"dep"],[19,21,"appos"],[19,23,"cc"],[19,26,"conj_and"],[19,28,"conj_and"],[19,31,"conj_and"],[19,33,"conj_and"],[19,34,"punct"],[19,35,"cc"],[19,37,"conj_and"],[21,20,"punct"],[21,22,"punct"],[26,24,"amod"],[26,25,"amod"],[26,27,"punct"],[26,28,"conj_and"],[26,29,"punct"],[26,31,"conj_and"],[26,32,"cc"],[26,33,"conj_and"],[31,30,"amod"],[37,36,"amod"],[37,40,"nmod_such_as"],[37,42,"nmod_such_as"],[37,44,"nmod_such_as"],[37,46,"nmod_such_as"],[37,48,"nmod_such_as"],[37,50,"nmod_such_as"],[37,56,"nmod_such_as"],[38,39,"mwe"],[40,38,"case"],[40,41,"punct"],[40,42,"conj_and"],[40,43,"punct"],[40,44,"conj_and"],[40,45,"punct"],[40,46,"conj_and"],[40,47,"punct"],[40,48,"conj_and"],[40,49,"punct"],[40,50,"conj_and"],[40,51,"cc"],[40,56,"conj_and"],[52,53,"mwe"],[52,54,"mwe"],[56,52,"det:qmod"],[56,55,"compound"]]}]}]}"""
    val doc = Document.fromJson(json)
    val ee = TestUtils.mkExtractorEngine(doc)
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
    val animals = mentions.map(m => ee.getString(m.luceneDocId, m.arguments("result").head))
    val expectedResults = List("rabbit", "possum", "quail", "badger", "iguana", "armadillo", "variety of river fish")
    animals should contain theSameElementsInOrderAs expectedResults
  }

}
