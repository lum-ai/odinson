package ai.lum.odinson.foundations


import ai.lum.odinson.DataGatherer.VerboseLevels
import ai.lum.odinson.lucene.index.OdinsonIndex
import ai.lum.odinson.test.utils.OdinsonTest
import ai.lum.odinson.{ExtractorEngine, Sentence, TokensField, Document => OdinsonDocument}
import com.typesafe.config.ConfigValueFactory
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterEach

import java.io.File

class TestNewExtractorEngineWithIncrementalIndex extends OdinsonTest with BeforeAndAfterEach {

    val testDataDir = {
        val file = new File( "./target/new_extractor_engine_test" )
        file.mkdirs()
        file
    }

    val testConfig =
        defaultConfig
          .withValue( "odinson.indexDir", ConfigValueFactory.fromAnyRef( testDataDir.getCanonicalPath ) )
          .withValue( "odinson.index.incremental", ConfigValueFactory.fromAnyRef( true ) )


    val docs : Seq[ OdinsonDocument ] = {
        val tokens = TokensField( rawTokenField, Array( "Rain", "causes", "flood" ) )
        val sentence = Sentence( tokens.tokens.length, Seq( TokensField( rawTokenField, Array( "Rain", "causes", "flood" ) ) ) )
        val doc1 = OdinsonDocument( "<TEST-ID1>", Nil, Seq( sentence ) )
        val doc2 = OdinsonDocument( "<TEST-ID2>", Nil, Seq( sentence ) )
        Seq( doc1, doc2 )
    }

    lazy val ee = ExtractorEngine.fromConfig( testConfig )

    override def afterEach( ) : Unit = {
        FileUtils.deleteDirectory( new File( testConfig.getString( "odinson.indexDir" ) ) )
    }

    private def writeTestIndex( docs : Seq[ OdinsonDocument ] ) : Unit = {
        val indexer = OdinsonIndex.fromConfig( testConfig )
        docs.foreach( indexer.indexOdinsonDoc )
        indexer.close()
    }

    "Odinson ExtractorEngine" should "run a simple query correctly" ignore {
        val docs : Seq[ OdinsonDocument ] = {
            val tokens = TokensField( rawTokenField, Array( "Rain", "causes", "flood" ) )
            val sentence = Sentence( tokens.tokens.length, Seq( TokensField( rawTokenField, Array( "Rain", "causes", "flood" ) ) ) )
            val doc1 = OdinsonDocument( "<TEST-ID1>", Nil, Seq( sentence ) )
            val doc2 = OdinsonDocument( "<TEST-ID2>", Nil, Seq( sentence ) )
            Seq( doc1, doc2 )
        }

        writeTestIndex( docs )

        val q = ee.mkQuery( "causes" )
        val results = ee.query( q, 1 )

        println( results.totalHits )
        results.totalHits should equal( 2 )
    }

    it should "getTokensFromSpan correctly from existing Field" in {
        // Becky ate gummy bears.
        val doc = getDocument( "becky-gummy-bears-v2" )
        writeTestIndex( Seq( doc ) )

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
        val extractors = ee.ruleReader.compileRuleString( rules )
        val mentions = getMentionsWithLabel( ee.extractAndPopulate( extractors, level = VerboseLevels.All ).toSeq, "Test" )
        mentions should have size ( 1 )

        val mention = mentions.head

        mention.text should be( "ate" )
        mention.mentionFields( "lemma" ) should contain only ( "eat" )
    }

    //    it should "getTokensFromSpan with OdinsonException from non-existing Field" in {
    //        // Becky ate gummy bears.
    //        val doc = getDocument( "becky-gummy-bears-v2" )
    //        val ee = extractorEngineWithConfigValue( doc, "odinson.index.storedFields", Seq( "raw", "lemma" ) )
    //        val rules =
    //            """
    //              |rules:
    //              |  - name: testrule
    //              |    type: event
    //              |    label: Test
    //              |    pattern: |
    //              |      trigger = [lemma=eat]
    //              |      subject: ^NP = >nsubj []
    //              |      object: ^NP = >dobj []
    //    """.stripMargin
    //        val extractors = ee.ruleReader.compileRuleString( rules )
    //        val mentions = getMentionsWithLabel( ee.extractMentions( extractors ).toSeq, "Test" )
    //        mentions should have size ( 1 )
    //
    //        val mention = mentions.head
    //
    //        an[ utils.exceptions.OdinsonException ] should be thrownBy ee.dataGatherer.getTokensForSpan(
    //            mention.luceneSegmentDocId,
    //            mention.odinsonMatch,
    //            fieldName = "notAField"
    //            )
    //    }
    //
    //    // TODO: implement index fixture to test the features bellow
    //    // TODO: def getParentDoc(docId: String)
    //    // TODO: def compileRules(rules: String)
    //    // TODO: def extractMentions(extractors: Seq[Extractor], numSentences: Int)
    //    // TODO: def query(odinsonQuery: OdinsonQuery, n: Int, after: OdinsonScoreDoc)
    //
    //    // TODO: def getArgument(mention: Mention, name: String)
    //    // TODO: ExtractorEngine.fromConfig
    //    // TODO: ExtractorEngine.fromConfig(path: String)
    //    // "Odinson ExtractorEngine" should "initialize correctly from config" in {
    //    // }
    //    // TODO: ExtractorEngine.fromConfig(config: Config)
}
