package ai.lum.odinson.foundations

import ai.lum.odinson.DataGatherer.VerboseLevels
import ai.lum.odinson.lucene.index.OdinsonIndex
import ai.lum.odinson.test.utils.OdinsonTest
import ai.lum.odinson.utils.exceptions.OdinsonException
import ai.lum.odinson.{ Sentence, TokensField, Document => OdinsonDocument }
import com.typesafe.config.ConfigValueFactory
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterEach

import java.io.File

class TestExtractorEngineWithPersistentIncrementalIndex
    extends OdinsonTest
    with BeforeAndAfterEach {

  val testDataDir = {
    val file = new File("./target/new_extractor_engine_test")
    file.mkdirs()
    file
  }

  val testConfig =
    defaultConfig
      .withValue("odinson.indexDir", ConfigValueFactory.fromAnyRef(testDataDir.getCanonicalPath))
      .withValue("odinson.index.incremental", ConfigValueFactory.fromAnyRef(true))
      .withValue("odinson.index.refreshMs", ConfigValueFactory.fromAnyRef(-1))

  val testDocs: Seq[OdinsonDocument] = {
    val tokens = TokensField(rawTokenField, Array("Rain", "causes", "flood"))
    val sentence = Sentence(
      tokens.tokens.length,
      Seq(TokensField(rawTokenField, Array("Rain", "causes", "flood")))
    )
    val doc1 = OdinsonDocument("<TEST-ID1>", Nil, Seq(sentence))
    val doc2 = OdinsonDocument("<TEST-ID2>", Nil, Seq(sentence))
    Seq(doc1, doc2)
  }

  override def beforeEach(): Unit = {
    try {
      FileUtils.deleteDirectory(new File(testConfig.getString("odinson.indexDir")))
    } catch {
      // Some files may still be open at this time and some operating systems will refuse to
      // delete them.  This can lead to exceptions which prevent the tests from running.
      case _: Throwable =>
    }
  }

  override def afterEach(): Unit = {
    try {
      FileUtils.deleteDirectory(new File(testConfig.getString("odinson.indexDir")))
    } catch {
      // Some files may still be open at this time and some operating systems will refuse to
      // delete them.  This can lead to exceptions which prevent the tests from running.
      case _: Throwable =>
    }
  }

  private def writeTestIndex(docs: Seq[OdinsonDocument]): Unit = {
    val indexer = OdinsonIndex.fromConfig(testConfig)
    docs.foreach(indexer.indexOdinsonDoc)
    indexer.close()
  }

  "Odinson ExtractorEngine" should "run a simple query correctly" in {
    val docs: Seq[OdinsonDocument] = {
      val tokens = TokensField(rawTokenField, Array("Rain", "causes", "flood"))
      val sentence = Sentence(
        tokens.tokens.length,
        Seq(TokensField(rawTokenField, Array("Rain", "causes", "flood")))
      )
      val doc1 = OdinsonDocument("<TEST-ID1>", Nil, Seq(sentence))
      val doc2 = OdinsonDocument("<TEST-ID2>", Nil, Seq(sentence))
      Seq(doc1, doc2)
    }
    writeTestIndex(docs)

    val extractorEngine = mkExtractorEngine(testConfig)
    val q = extractorEngine.mkQuery("causes")
    val results = extractorEngine.query(q, 1)

    println(results.totalHits)
    results.totalHits should equal(2)

    extractorEngine.close()
  }

  it should "getTokensFromSpan correctly from existing Field" in {
    // Becky ate gummy bears.
    val doc = getDocument("becky-gummy-bears-v2")
    writeTestIndex(Seq(doc))

    val rules =
      """
              |rules:
              |  - name: testrule
              |    type: event
              |    label: Test
              |    pattern: |
              |      trigger = [lemma=eat]
              |      subject: ^NP = >nsubj []
              |      object: ^NP = >dobj []
        """.stripMargin

    val extractorEngine = mkExtractorEngine(testConfig)
    val extractors = extractorEngine.ruleReader.compileRuleString(rules)
    val mentions = getMentionsWithLabel(
      extractorEngine.extractAndPopulate(extractors, level = VerboseLevels.All).toSeq,
      "Test"
    )
    mentions should have size (1)

    val mention = mentions.head

    mention.text should be("ate")
    mention.mentionFields("lemma") should contain only ("eat")
    extractorEngine.close()
  }

  it should "getTokensFromSpan with OdinsonException from non-existing Field" in {
    // Becky ate gummy bears.
    //        val ee = extractorEngineWithConfigValue( doc, "odinson.index.storedFields", Seq( "raw", "lemma" ) )
    val rules =
      """
          |rules:
          |  - name: testrule
          |    type: event
          |    label: Test
          |    pattern: |
          |      trigger = [lemma=eat]
          |      subject: ^NP = >nsubj []
          |      object: ^NP = >dobj []
        """.stripMargin

    val gummyBears = getDocument("becky-gummy-bears-v2")
    writeTestIndex(Seq(gummyBears))

    val extractorEngine = mkExtractorEngine(testConfig)
    val extractors = extractorEngine.ruleReader.compileRuleString(rules)
    val mentions = getMentionsWithLabel(extractorEngine.extractMentions(extractors).toSeq, "Test")
    mentions should have size (1)

    val mention = mentions.head

    an[OdinsonException] should be thrownBy extractorEngine.dataGatherer.getTokensForSpan(
      mention.luceneSegmentDocId,
      mention.odinsonMatch,
      fieldName = "notAField"
    )

    extractorEngine.close()
  }

  //    // TODO: implement index fixture to test the features bellow
  //    // TODO: def getParentDoc(docId: String)
  //    // TODO: def compileRules(rules: String)
  //    // TODO: def extractMentions(extractors: Seq[Extractor], numSentences: Int)
  //    // TODO: def query(odinsonQuery: OdinsonQuery, n: Int, after: OdinsonScoreDoc)
  //
  //    // TODO: def getArgument(mention: Mention, name: String)
  //    // TODO: mkExtractorEngine
  //    // TODO: mkExtractorEngine(path: String)
  //    // "Odinson ExtractorEngine" should "initialize correctly from config" in {
  //    // }
  //    // TODO: mkExtractorEngine(config: Config)
}
