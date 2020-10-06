package ai.lum.odinson.events


import ai.lum.odinson.EventMatch
import ai.lum.odinson.OdinsonMatch
import ai.lum.odinson.StateMatch
import ai.lum.odinson.mention.Mention

class TestEventTriggers extends EventSpec {
  /** Returns a rule with a template
   *
   *  @param varsResult what to put in vars -> result -> <?>
   *  @param rulesPattern what to put in rules -> pattern <?>
   */
  def applyRuleTemplate(varsResult: String, rulesPattern: String): String = s"""
      |vars:
      |  result: $varsResult
      |rules:
      |  - name: example-rule
      |    type: basic
      |    pattern: |
      |      $rulesPattern
      |""".stripMargin

  /** Returns a rule with a template
   *
   *  @param varsResult what to put in vars -> result -> <?>
   *  @param rulesPatternTrigger what to put in rules -> pattern -> trigger <?>
   *  @param rulesPatternResult what to put in rules -> pattern -> result <?>
   */
   def applyRuleTemplate(varsResult: String, rulesPatternTrigger: String, rulesPatternResult: String) = s"""
      |vars:
      |  result: $varsResult
      |rules:
      |  - name: example-rule
      |    type: event
      |    pattern: |
      |      trigger = $rulesPatternTrigger 
      |      result = $rulesPatternResult
      |""".stripMargin
  
    /** Returns extractor engine */
    def mkExtractorEngine(docNum: String) = {
      val odinsonDocument = getDocument(docNum)
      val extractorEngine = Utils.mkExtractorEngine(odinsonDocument)
      extractorEngine
    }

  "Odinson" should "match events for all trigger mentions using a basic pattern" in {
    val ee = mkExtractorEngine("hedgehogs-coypy")

    val rules = applyRuleTemplate(
      rulesPattern = "animals >nmod_such_as >/conj.*/? (?<result>${result})",
      varsResult = "([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)"
    )

    val extractors = ee.ruleReader.compileRuleString(rules)
    val mentions = ee.extractMentions(extractors).toArray
    val animals = mentions.map(m => ee.getStringForSpan(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    val expectedResults = List("hedgehogs", "coypu", "wild cloven-footed animals", "deer", "zoo animals")
    animals should contain theSameElementsInOrderAs expectedResults
    ee.close()
  }

  it should "match events for all trigger mentions using an event pattern" in {
    val ee = mkExtractorEngine("hedgehogs-coypy")

    val rules = applyRuleTemplate(
      varsResult = "([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)",
      rulesPatternTrigger = "animals",
      rulesPatternResult = ">nmod_such_as >/conj.*/? ${result}",
    )

    val extractors = ee.ruleReader.compileRuleString(rules)
    val mentions = ee.extractMentions(extractors).toArray
    val animals = mentions.map(m => ee.getStringForSpan(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    val expectedResults = List("hedgehogs", "coypu", "wild cloven-footed animals", "deer", "zoo animals")
    animals should contain theSameElementsInOrderAs expectedResults
    ee.close()
  }

  it should "match events for all trigger mentions using an event pattern with quantifiers in the trigger" in {
    val ee = mkExtractorEngine("hedgehogs-coypy")

    val rules = applyRuleTemplate(
      varsResult = "([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)",
      rulesPatternTrigger = "wild? animals",
      rulesPatternResult = ">nmod_such_as >/conj.*/? ${result}",
    )

    val extractors = ee.ruleReader.compileRuleString(rules)
    val mentions = ee.extractMentions(extractors).toArray
    val animals = mentions.map(m => ee.getStringForSpan(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    val expectedResults = List("hedgehogs", "coypu", "wild cloven-footed animals", "deer", "zoo animals")
    animals should contain theSameElementsInOrderAs expectedResults
    ee.close()
  }

  it should "match events for all trigger mentions using an event pattern with quantifiers in the trigger (variable right hand side)" in {
    val ee = mkExtractorEngine("hedgehogs-coypy")

    val rules = applyRuleTemplate(
      varsResult = "([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)",
      rulesPatternTrigger = "[tag=JJ]* ([tag=NNS] [tag=JJ])?",
      rulesPatternResult = ">nmod_such_as >/conj.*/? ${result}",
    )

    val extractors = ee.ruleReader.compileRuleString(rules)
    val mentions = ee.extractMentions(extractors).toArray
    val triggers = mentions.map(m => ee.getStringForSpan(m.luceneDocId, getTrigger(m)))
    val animals = mentions.map(m => ee.getStringForSpan(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    val expectedTriggers = List("wild animals such", "wild animals such", "wild animals such", "wild cloven-footed animals such", "wild cloven-footed animals such")
    val expectedResults = List("hedgehogs", "coypu", "wild cloven-footed animals", "deer", "zoo animals")
    triggers should contain theSameElementsInOrderAs expectedTriggers
    animals should contain theSameElementsInOrderAs expectedResults

    ee.close()
  }

  it should "match events with quantifiers in the trigger (overlap with different start and end)" in {
    val ee = mkExtractorEngine("hedgehogs-coypy")

    val rules = applyRuleTemplate(
      varsResult = "([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)",
      rulesPatternTrigger = "[tag=DT | tag=JJ] [tag=JJ]",
      rulesPatternResult = "<amod [lemma=animal]",
    )

    val extractors = ee.ruleReader.compileRuleString(rules)
    val mentions = ee.extractMentions(extractors).toArray
    val triggers = mentions.map(m => ee.getStringForSpan(m.luceneDocId, getTrigger(m)))
    val animals = mentions.map(m => ee.getStringForSpan(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    val expectedTriggers = List("Some wild", "any wild")
    val expectedResults = List("animals", "animals")
    triggers should contain theSameElementsInOrderAs expectedTriggers
    animals should contain theSameElementsInOrderAs expectedResults

    ee.close()
  }

  it should "match events with quantifiers in the trigger (greedy)" in {
    val ee = mkExtractorEngine("hedgehogs-coypy")

    val rules = applyRuleTemplate(
      varsResult = "([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)",
      rulesPatternTrigger = "some []* animals",
      rulesPatternResult = "(<nmod_such_as | >nmod_including) >/conj.*/? ${result}",
    )
    val extractors = ee.ruleReader.compileRuleString(rules)

    val mentions = ee.extractMentions(extractors).toArray
    val triggers = mentions.map(m => ee.getStringForSpan(m.luceneDocId, getTrigger(m)))
    val animals = mentions.map(m => ee.getStringForSpan(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    val expectedTrigger = "Some wild animals such as hedgehogs , coypu , and any wild cloven-footed animals such as deer and zoo animals"
    val expectedResults = List("wild animals", "wild cloven-footed animals", "elephants")
    triggers.foreach { trigger => trigger should be (expectedTrigger) }
    animals should contain theSameElementsInOrderAs expectedResults

    ee.close()
  }

  it should "match events with quantifiers in the trigger (greedy; allow trigger overlaps)" in {
    val ee = mkExtractorEngine("hedgehogs-coypy")

    val rules = applyRuleTemplate(
      varsResult = "([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)",
      rulesPatternTrigger = "some []* animals",
      rulesPatternResult = "(>nmod_such_as | >nmod_including) >/conj.*/? ${result}",
    )
    val extractors = ee.ruleReader.compileRuleString(rules)
    val mentions = ee.extractMentions(extractors, allowTriggerOverlaps = true).toArray
    val triggers = mentions.map(m => ee.getStringForSpan(m.luceneDocId, getTrigger(m)))
    val animals = mentions.map(m => ee.getStringForSpan(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    val expectedTriggers: List[String] = (1 to 6).map(
      (m) =>  "Some wild animals such as hedgehogs , coypu , and any wild cloven-footed animals such as deer and zoo animals"
    ).toList

    val expectedResults = List("hedgehogs", "coypu", "wild cloven-footed animals", "deer", "zoo animals", "elephants")
    triggers should contain theSameElementsInOrderAs expectedTriggers
    animals should contain theSameElementsInOrderAs expectedResults

    ee.close()
  }

  def getTrigger(mention: Mention): OdinsonMatch = mention.odinsonMatch

  it should "match events for all trigger mentions using an event pattern with quantifiers in the trigger (laziness)" in {
    val ee = mkExtractorEngine("hedgehogs-coypy")

    val rules = applyRuleTemplate(
      varsResult = "([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)",
      rulesPatternTrigger = "some []*? animals",
      rulesPatternResult = ">nmod_such_as >/conj.*/? ${result}",
    )
    val extractors = ee.ruleReader.compileRuleString(rules)
    val mentions = ee.extractMentions(extractors).toArray
    val triggers = mentions.map(m => ee.getStringForSpan(m.luceneDocId, getTrigger(m)))
    val animals = mentions.map(m => ee.getStringForSpan(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    val expectedTriggers = List("Some wild animals", "Some wild animals", "Some wild animals")
    val expectedResults = List("hedgehogs", "coypu", "wild cloven-footed animals")
    triggers should contain theSameElementsInOrderAs expectedTriggers
    animals should contain theSameElementsInOrderAs expectedResults

    ee.close()
  }

  it should "match arguments of correct length using a basic pattern (i)" in {
    val ee = mkExtractorEngine("pre-european-diet")

    val rules = applyRuleTemplate(
      varsResult = "([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)",
      rulesPattern = "animals >nmod_such_as >/conj.*/? (?<result>${result})",
    )
    val extractors = ee.ruleReader.compileRuleString(rules)
    val mentions = ee.extractMentions(extractors).toArray
    val animals = mentions.map(m => ee.getStringForSpan(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    val expectedResults = List("rabbit", "possum", "quail", "badger", "iguana", "armadillo", "variety of river fish")
    animals should contain theSameElementsInOrderAs expectedResults

    ee.close()
  }

  it should "match arguments of correct length using an event pattern (i)" in {
    val ee = mkExtractorEngine("pre-european-diet")
    
    val rules = applyRuleTemplate(
      varsResult = "([tag=/J.*/]{,3} [tag=/N.*/]+ (of [tag=DT]? [tag=/J.*/]{,3} [tag=/N.*/]+)?)",
      rulesPatternTrigger = "animals",
      rulesPatternResult = ">nmod_such_as >/conj.*/? ${result}",
    )

    val extractors = ee.ruleReader.compileRuleString(rules)
    val mentions = ee.extractMentions(extractors).toArray
    val animals = mentions.map(m => ee.getStringForSpan(m.luceneDocId, m.arguments("result").head.odinsonMatch))
    val expectedResults = List("rabbit", "possum", "quail", "badger", "iguana", "armadillo", "variety of river fish")
    animals should contain theSameElementsInOrderAs expectedResults

    ee.close()
  }

}
