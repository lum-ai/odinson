package ai.lum.odinson.foundations

import org.scalatest._

import ai.lum.odinson.BaseSpec
import ai.lum.odinson.events.TestEvents._

class TestRuleFile extends BaseSpec {
  val json = """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}"""

  // extractor engine persists across tests (hacky way)
  val doc = getDocumentFromJson(json)
  val ee = Utils.mkExtractorEngine(doc)

  "Odinson" should "match event with rules defined in a rule file" in {
    val rules = """
      |vars:
      |  chunk: "[chunk=B-NP][chunk=I-NP]*"
      |
      |rules:
      |  - name: testrule
      |    type: event
      |    pattern: |
      |      trigger = [lemma=eat]
      |      subject: ^NP = >nsubj ${chunk}
      |      object: ^NP = >dobj ${chunk}
    """.stripMargin
    val extractors = ee.ruleReader.compileRuleFile(rules)
    val mentions = ee.extractMentions(extractors)
    mentions should have size 1
    val m = mentions.head.odinsonMatch
    // test trigger
    testEventTrigger(m, start = 1, end = 2)
    // test arguments
    val desiredArgs = Seq(
      Argument("subject", 0, 1),
      Argument("object", 2, 4),
    )
    testEventArguments(m, desiredArgs)
  }

  it should "correctly handle list vars" in {
    // Leonardo leads, Donatello does machines (That's a fact, jack!)
    val json = "{\"id\":\"d773073c-1d76-446e-b1bc-7a6347516ba3\",\"metadata\":[],\"sentences\":[{\"numTokens\":14,\"fields\":[{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"raw\",\"tokens\":[\"Leonardo\",\"leads\",\",\",\"Donatello\",\"does\",\"machines\",\"(\",\"That\",\"'s\",\"a\",\"fact\",\",\",\"jack\",\"!\"],\"store\":true},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"word\",\"tokens\":[\"Leonardo\",\"leads\",\",\",\"Donatello\",\"does\",\"machines\",\"(\",\"That\",\"'s\",\"a\",\"fact\",\",\",\"jack\",\"!\"]},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"tag\",\"tokens\":[\"NNP\",\"VBZ\",\",\",\"NNP\",\"VBZ\",\"NNS\",\"-LRB-\",\"DT\",\"VBZ\",\"DT\",\"NN\",\",\",\"NN\",\".\"]},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"lemma\",\"tokens\":[\"Leonardo\",\"lead\",\",\",\"Donatello\",\"do\",\"machine\",\"(\",\"that\",\"be\",\"a\",\"fact\",\",\",\"jack\",\"!\"]},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"entity\",\"tokens\":[\"PERSON\",\"O\",\"O\",\"PERSON\",\"O\",\"O\",\"O\",\"O\",\"O\",\"O\",\"O\",\"O\",\"PERSON\",\"O\"]},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"chunk\",\"tokens\":[\"B-NP\",\"B-VP\",\"O\",\"B-NP\",\"B-VP\",\"B-NP\",\"O\",\"B-NP\",\"B-VP\",\"B-NP\",\"I-NP\",\"O\",\"B-NP\",\"O\"]},{\"$type\":\"ai.lum.odinson.GraphField\",\"name\":\"dependencies\",\"edges\":[[1,0,\"nsubj\"],[1,2,\"punct\"],[1,4,\"ccomp\"],[4,3,\"nsubj\"],[4,5,\"dobj\"],[5,10,\"dep\"],[10,6,\"punct\"],[10,7,\"nsubj\"],[10,8,\"cop\"],[10,9,\"det\"],[10,11,\"punct\"],[10,12,\"appos\"],[10,13,\"punct\"]],\"roots\":[1]}]},{\"numTokens\":1,\"fields\":[{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"raw\",\"tokens\":[\")\"],\"store\":true},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"word\",\"tokens\":[\")\"]},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"tag\",\"tokens\":[\"-RRB-\"]},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"lemma\",\"tokens\":[\")\"]},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"entity\",\"tokens\":[\"O\"]},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"chunk\",\"tokens\":[\"O\"]},{\"$type\":\"ai.lum.odinson.GraphField\",\"name\":\"dependencies\",\"edges\":[],\"roots\":[0]}]}]}"
    val doc = getDocumentFromJson(json)
    val ee = Utils.mkExtractorEngine(doc)
    val rules =
      """
        |vars:
        |  turtle:
        |     - leonardo
        |     - donatello
        |     - raphael
        |     - michelangelo
        |
        |rules:
        |  - name: "turtle-power-var"
        |    label: MutantTurtle
        |    type: basic
        |    pattern: |
        |      [norm=/${turtle}/]
       """.stripMargin
    val extractors = ee.ruleReader.compileRuleFile(rules)
    val mentions = ee.extractMentions(extractors)
    mentions should have size 2
  }

}
