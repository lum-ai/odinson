package ai.lum.odinson

import org.scalatest._

class TestEventTriggers extends FlatSpec with Matchers {

  import TestEvents._

  val json = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":27,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Some","wild","animals","such","as","hedgehogs",",","coypu",",","and","any","wild","cloven-footed","animals","such","as","deer","and","zoo","animals","including","elephants","can","also","contract","it","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Some","wild","animals","such","as","hedgehogs",",","coypu",",","and","any","wild","cloven-footed","animals","such","as","deer","and","zoo","animals","including","elephants","can","also","contract","it","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["DT","JJ","NNS","JJ","IN","NNS",",","NN",",","CC","DT","JJ","JJ","NNS","JJ","IN","NNS","CC","NN","NNS","VBG","NNS","MD","RB","VB","PRP","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["some","wild","animal","such","as","hedgehog",",","coypu",",","and","any","wild","cloven-footed","animal","such","as","deer","and","zoo","animal","include","elephant","can","also","contract","it","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","I-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","O","O","B-NP","I-NP","I-NP","I-NP","B-PP","I-PP","B-NP","O","B-NP","I-NP","B-PP","B-NP","B-VP","I-VP","I-VP","B-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","roots":[24],"edges":[[2,0,"det"],[2,1,"amod"],[2,5,"nmod_such_as"],[2,7,"nmod_such_as"],[2,13,"nmod_such_as"],[3,4,"mwe"],[5,3,"case"],[5,6,"punct"],[5,7,"conj_and"],[5,8,"punct"],[5,9,"cc"],[5,13,"conj_and"],[13,10,"det"],[13,11,"amod"],[13,12,"amod"],[13,16,"nmod_such_as"],[13,19,"nmod_such_as"],[14,15,"mwe"],[16,14,"case"],[16,17,"cc"],[16,19,"conj_and"],[16,21,"nmod_including"],[19,18,"compound"],[21,20,"case"],[24,2,"nsubj"],[24,22,"aux"],[24,23,"advmod"],[24,25,"dobj"],[24,26,"punct"]]}]}]}"""

  val doc = Document.fromJson(json)
  val ee = TestUtils.mkExtractorEngine(doc)

  "Odinson" should "match events for all trigger mentions" in {
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
    mentions should have size 5
  }

}
