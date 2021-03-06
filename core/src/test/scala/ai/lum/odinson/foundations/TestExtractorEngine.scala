package ai.lum.odinson.foundations

import ai.lum.odinson.DataGatherer.VerboseLevels
import ai.lum.odinson.{ Document, ExtractorEngine, Sentence, TokensField, utils }
import ai.lum.odinson.utils.TestUtils.OdinsonTest

class TestExtractorEngine extends OdinsonTest {

  // create a test sentence
  val text = "Rain causes flood"
  val tokens = TokensField(rawTokenField, text.split(" "))
  val sentence = Sentence(tokens.tokens.length, Seq(tokens))
  val doc1 = Document("<TEST-ID1>", Nil, Seq(sentence))
  val doc2 = Document("<TEST-ID2>", Nil, Seq(sentence))

  val ee = ExtractorEngine.inMemory(Seq(doc1, doc2))

  "Odinson ExtractorEngine" should "run a simple query correctly" in {
    val q = ee.mkQuery("causes")
    val results = ee.query(q, 1)
    results.totalHits should equal(2)
  }

  it should "getTokensFromSpan correctly from existing Field" in {
    // Becky ate gummy bears.
    val doc = getDocument("becky-gummy-bears-v2")
    val ee = extractorEngineWithConfigValue(doc, "odinson.index.storedFields", Seq("raw", "lemma"))
    val rules = """
        |rules:
        |  - name: testrule
        |    type: event
        |    label: Test
        |    pattern: |
        |      trigger = [lemma=eat]
        |      subject: ^NP = >nsubj []
        |      object: ^NP = >dobj []
    """.stripMargin
    val extractors = ee.ruleReader.compileRuleString(rules)
    val mentions = getMentionsWithLabel(
      ee.extractAndPopulate(extractors, level = VerboseLevels.All).toSeq,
      "Test"
    )
    mentions should have size (1)

    val mention = mentions.head

    mention.text should be("ate")
    mention.mentionFields("lemma") should contain only ("eat")

  }

  it should "getTokensFromSpan with OdinsonException from non-existing Field" in {
    // Becky ate gummy bears.
    val doc = getDocument("becky-gummy-bears-v2")
    val ee = extractorEngineWithConfigValue(doc, "odinson.index.storedFields", Seq("raw", "lemma"))
    val rules = """
      |rules:
      |  - name: testrule
      |    type: event
      |    label: Test
      |    pattern: |
      |      trigger = [lemma=eat]
      |      subject: ^NP = >nsubj []
      |      object: ^NP = >dobj []
    """.stripMargin
    val extractors = ee.ruleReader.compileRuleString(rules)
    val mentions = getMentionsWithLabel(ee.extractMentions(extractors).toSeq, "Test")
    mentions should have size (1)

    val mention = mentions.head

    an[utils.exceptions.OdinsonException] should be thrownBy ee.dataGatherer.getTokensForSpan(
      mention.luceneSegmentDocId,
      mention.odinsonMatch,
      fieldName = "notAField"
    )
  }

  // TODO: implement index fixture to test the features bellow
  // TODO: def getParentDoc(docId: String)
  // TODO: def compileRules(rules: String)
  // TODO: def extractMentions(extractors: Seq[Extractor], numSentences: Int)
  // TODO: def query(odinsonQuery: OdinsonQuery, n: Int, after: OdinsonScoreDoc)

  // TODO: def getArgument(mention: Mention, name: String)
  // TODO: ExtractorEngine.fromConfig
  // TODO: ExtractorEngine.fromConfig(path: String)
  // "Odinson ExtractorEngine" should "initialize correctly from config" in {
  // }
  // TODO: ExtractorEngine.fromConfig(config: Config)
}
