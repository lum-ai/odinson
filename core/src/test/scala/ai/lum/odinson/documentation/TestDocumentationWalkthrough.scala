package ai.lum.odinson.documentation

import ai.lum.odinson.Document
import ai.lum.odinson.utils.TestUtils.OdinsonTest

class TestDocumentationWalkthrough extends OdinsonTest {

  val text = "Sally loves dogs.  Yesterday, Sally adopted a cat named Ajax."

  val json =
    "{\"id\":\"0e2fa480-e8ac-4f6b-9ebb-057341d8c466\",\"metadata\":[],\"sentences\":[{\"numTokens\":4,\"fields\":[{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"raw\",\"tokens\":[\"Sally\",\"loves\",\"dogs\",\".\"]},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"word\",\"tokens\":[\"Sally\",\"loves\",\"dogs\",\".\"]},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"tag\",\"tokens\":[\"NNP\",\"VBZ\",\"NNS\",\".\"]},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"lemma\",\"tokens\":[\"Sally\",\"love\",\"dog\",\".\"]},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"entity\",\"tokens\":[\"PERSON\",\"O\",\"O\",\"O\"]},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"chunk\",\"tokens\":[\"B-NP\",\"B-VP\",\"B-NP\",\"O\"]},{\"$type\":\"ai.lum.odinson.GraphField\",\"name\":\"dependencies\",\"edges\":[[1,0,\"nsubj\"],[1,2,\"dobj\"],[1,3,\"punct\"]],\"roots\":[1]}]},{\"numTokens\":9,\"fields\":[{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"raw\",\"tokens\":[\"Yesterday\",\",\",\"Sally\",\"adopted\",\"a\",\"cat\",\"named\",\"Ajax\",\".\"]},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"word\",\"tokens\":[\"Yesterday\",\",\",\"Sally\",\"adopted\",\"a\",\"cat\",\"named\",\"Ajax\",\".\"]},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"tag\",\"tokens\":[\"NN\",\",\",\"NNP\",\"VBD\",\"DT\",\"NN\",\"VBN\",\"NNP\",\".\"]},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"lemma\",\"tokens\":[\"yesterday\",\",\",\"Sally\",\"adopt\",\"a\",\"cat\",\"name\",\"Ajax\",\".\"]},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"entity\",\"tokens\":[\"DATE\",\"O\",\"PERSON\",\"O\",\"O\",\"O\",\"O\",\"ORGANIZATION\",\"O\"]},{\"$type\":\"ai.lum.odinson.TokensField\",\"name\":\"chunk\",\"tokens\":[\"B-NP\",\"O\",\"B-NP\",\"B-VP\",\"B-NP\",\"I-NP\",\"B-VP\",\"B-NP\",\"O\"]},{\"$type\":\"ai.lum.odinson.GraphField\",\"name\":\"dependencies\",\"edges\":[[3,2,\"nsubj\"],[3,5,\"dobj\"],[3,8,\"punct\"],[3,0,\"nmod:tmod\"],[3,1,\"punct\"],[5,4,\"det\"],[5,6,\"acl\"],[6,7,\"xcomp\"]],\"roots\":[3]}]}]}"

  val extractorEngine = mkExtractorEngine(Document.fromJson(json))

  val rules = """
      |rules:
      |  - name: pets_type
      |    type: basic
      |    label: Pet  # if found, will have the label "Pet"
      |    priority: 1 # will run in the first round of extraction
      |    pattern: |
      |       [lemma=/cat|dog|bunny|fish/]
      |
      |  - name: pets_adoption
      |    type: event
      |    label: Adoption
      |    priority: 2  # will run in the second round of extraction, can reference priority 1 rules
      |    pattern: |
      |      trigger = [lemma=adopt]
      |      adopter = >nsubj []   # note: we didn't specify the label, so any token will work
      |      pet: Pet = >dobj []
    """.stripMargin

  val extractors = extractorEngine.compileRuleString(rules)
  val mentions = extractorEngine.extractMentions(extractors).toSeq

  behavior of "documentation"

  it should "find the dog mention" in {
    val dogs = getMentionsWithStringValue(mentions, "dogs", extractorEngine)
    dogs should have size (1)
  }

  it should "find the cat mention" in {
    val cats = getMentionsWithStringValue(mentions, "cat", extractorEngine)
    cats should have size (1)
  }

  it should "find the adoption event" in {
    val adoptions = getMentionsWithStringValue(mentions, "adopted", extractorEngine)
    adoptions should have size (1)
    testMention(
      adoptions.head,
      "adopted",
      Seq(Argument("adopter", "Sally"), Argument("pet", "cat")),
      extractorEngine
    )
  }

}
