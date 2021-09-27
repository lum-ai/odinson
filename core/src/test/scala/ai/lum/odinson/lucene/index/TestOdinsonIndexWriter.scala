package ai.lum.odinson.lucene.index

import ai.lum.odinson._
import ai.lum.odinson.serialization.UnsafeSerializer
import ai.lum.odinson.test.utils.OdinsonTest
import ai.lum.odinson.utils.exceptions.OdinsonException
import com.typesafe.config.{Config, ConfigValueFactory}
import org.scalatest.BeforeAndAfterEach
// lum imports
// file imports
import java.io.File
import scala.collection.JavaConverters._

class TestOdinsonIndexWriter extends OdinsonTest with BeforeAndAfterEach {
    type Fixture = OdinsonIndexWriter

    val testIndexDir : File = new File( "./target/odinson_index_writer_test" )

    val testConfig : Config = defaultConfig.withValue( "odinson.indexDir", ConfigValueFactory.fromAnyRef( testIndexDir.getAbsolutePath ) )

    override def beforeEach( ) : Unit = {
        testIndexDir.delete()
    }

    override def afterEach( ) : Unit = {
        testIndexDir.delete()
    }

    def getOdinsonIndexWriter( config : Config = testConfig ) : OdinsonIndexWriter = {
        OdinsonIndexWriter.fromConfig( config )
    }

    it should "return index from config correctly" in {
        val indexWriter = getOdinsonIndexWriter()
        // make sure the folder was created with only the locker inside
        indexWriter.directory.listAll should contain( "write.lock" )

        indexWriter.close()
    }

    it should "mkLuceneFields should convert Fields to lucene.Fields correctly" in {
        val indexWriter = getOdinsonIndexWriter()
        // Initialize fild of type DateField
        var field =
            """{"$type":"ai.lum.odinson.DateField","name":"smth","date":"1993-03-28"}"""
        //
        val dateField = DateField.fromJson( field )
        // DateField
        val luceneDateField = indexWriter.mkLuceneFields( dateField, false )
        // test
        luceneDateField.head.name shouldEqual ( "smth" )
        // Initialize field of type StringField
        field =
          """{"$type":"ai.lum.odinson.StringField","name":"smth","string":"foo"}"""
        // StringField
        val stringField = StringField.fromJson( field )
        val luceneStringField = indexWriter.mkLuceneFields( stringField, false )
        luceneStringField.head.name shouldEqual ( "smth" )
        // TODO: should we test more stuff
        indexWriter.close()
    }

    it should "store stored fields and not others" in {
        val customConfig : Config = testConfig.withValue( "odinson.index.storedFields", ConfigValueFactory.fromAnyRef( Seq( "tag", "raw" ).asJava ) )
        val writer = OdinsonIndexWriter.fromConfig( customConfig )
        writer.addDocuments( writer.mkDocumentBlock( getDocument( "rainbows" ) ) )
        writer.close()

        val extractorEngine = mkExtractorEngine( customConfig )

        // we asked it to store `tag` so the extractor engine should be able to access the content
        extractorEngine.dataGatherer.getTokensForSpan( 0, "tag", 0, 1 ) should contain only "NNS"
        // though `entity` is a field in the Document, it wasn't stored, so the extractor engine shouldn't
        // be able to retrieve the content
        an[ OdinsonException ] should be thrownBy extractorEngine.dataGatherer.getTokensForSpan( 0, "entity", 0, 1 )

        extractorEngine.close()
    }

    it should "throw an exception if the displayField isn't in the storedFields" in {
        val customConfig : Config = testConfig.withValue( "odinson.index.storedFields", ConfigValueFactory.fromAnyRef( Seq( "apple", "banana", "kiwi" ).asJava ) )

        an[ OdinsonException ] shouldBe thrownBy {
            OdinsonIndexWriter.fromConfig( customConfig )
        }
    }

    it should "store and retrieve large graphs" in {
        val numTokens = 4000
        val large = Array.fill[ String ]( numTokens )( "test" )
        large( 0 ) = "start"
        large( 3 ) = "success"
        val raw = TokensField( "raw", large.toSeq )
        val word = TokensField( "word", large.toSeq )

        val deps = new Array[ (Int, Int, String) ]( numTokens )
        for ( i <- 0 until numTokens - 1 ) {
            if ( i < 3 ) {
                // (0,1,edge)
                deps( i ) = (i, i + 1, "edge")
            } else {
                deps( i ) = (i, i + 1, "nomatch")
            }
        }
        val roots = Set[ Int ]( 0 )
        val graph = GraphField( "dependencies", deps, roots )

        val sent = Sentence( numTokens, Seq( raw, word, graph ) )
        val doc = Document( "testdoc", Seq.empty, Seq( sent ) )

        // Yes, in fact the deps field is above the previous threshold
        val incomingEdges = graph.mkIncomingEdges( numTokens )
        val outgoingEdges = graph.mkOutgoingEdges( numTokens )
        val indexWriter = getOdinsonIndexWriter()

        val directedGraph = indexWriter.mkDirectedGraph( incomingEdges, outgoingEdges, roots.toArray )
        val bytes = UnsafeSerializer.graphToBytes( directedGraph )
        // previously, we only supported up to this: sortedDocValuesFieldMaxSize = 32766
        bytes.length should be > ( 32766 )
        indexWriter.close()

        // ensure the EE can use it
        val ee = extractorEngineWithConfigValue( doc, "odinson.index.maxNumberOfTokensPerSentence", numTokens )
        val rules =
            """
              |rules:
              |  - name: testrule
              |    type: basic
              |    label: Test
              |    pattern: |
              |      start >edge+ success
              |""".stripMargin
        val extractors = ee.compileRuleString( rules )
        val results = ee.extractNoState( extractors ).toArray
        results.length should be( 1 )
        ee.dataGatherer.getTokensForSpan( results.head ) should contain only "success"
    }
}
