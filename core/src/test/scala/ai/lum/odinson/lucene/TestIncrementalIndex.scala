package ai.lum.odinson.lucene

import ai.lum.odinson.lucene.index.{IncrementalOdinsonIndex, OdinsonIndex}
import ai.lum.odinson.utils.TestUtils.OdinsonTest
import com.typesafe.config.{Config, ConfigValueFactory}
import org.scalatest.BeforeAndAfterAll

import java.io.File
import java.nio.file.Files
import scala.reflect.io.Directory

class TestIncrementalIndex extends OdinsonTest with BeforeAndAfterAll {
    type Fixture = IncrementalOdinsonIndex

    val tmpFolder : File = Files.createTempDirectory( "odinson-test" ).toFile
    val indexDir : File = new File( tmpFolder, "index" )

    val testConfig : Config = {
        // re-compute the index and docs path's
        defaultConfig
          .withValue( "odinson.indexDir", ConfigValueFactory.fromAnyRef( indexDir.getAbsolutePath ) )
          .withValue( "odinson.index.incremental", ConfigValueFactory.fromAnyRef( true ) )
    }

    override def afterAll( ) : Unit = {
        val dir = new Directory( indexDir )
        dir.deleteRecursively()
    }

    def setupOdinsonIndex( ) : OdinsonIndex = {
        OdinsonIndex.fromConfig( testConfig )
    }


    "OdinsonIndexWriter" should "object should return index from config correctly" in {
        val index = setupOdinsonIndex()
        index.directory.listAll.head shouldBe "write.lock"
        index.close()
    }

    it should "mkLuceneFields should convert Fields to lucene.Fields correctly" in {
        //        val indexWriter = setupOdinsonIndex
        //        // Initialize fild of type DateField
        //        var field =
        //            """{"$type":"ai.lum.odinson.DateField","name":"smth","date":"1993-03-28"}"""
        //        //
        //        val dateField = DateField.fromJson( field )
        //        // DateField
        //        val luceneDateField = indexWriter.mkLuceneFields( dateField, false )
        //        // test
        //        luceneDateField.head.name shouldEqual ( "smth" )
        //        // Initialize field of type StringField
        //        field =
        //          """{"$type":"ai.lum.odinson.StringField","name":"smth","string":"foo"}"""
        //        // StringField
        //        val stringField = StringField.fromJson( field )
        //        val luceneStringField = indexWriter.mkLuceneFields( stringField, false )
        //        luceneStringField.head.name shouldEqual ( "smth" )
        //        // TODO: should we test more stuff
        //        indexWriter.close()
        //        deleteIndex
    }

    it should "replace invalid characters prior to indexing to prevent off-by-one errors" in {
        //        val doc = getDocument( "bad-character" )
        //
        //        def ee = mkExtractorEngine( doc )
        //
        //        val pattern = "complex <nsubj phosphorylate >dobj []"
        //        val expectedMatches = Array( "AKT1" )
        //
        //        val query = ee.mkQuery( pattern )
        //        val results = ee.query( query, 1 )
        //        results.totalHits should equal( 1 )
        //
        //        val matches = results.scoreDocs.head.matches
        //        val docId = results.scoreDocs.head.doc
        //        val foundStrings = matches.map( m => ee.getStringForSpan( docId, m ) )
        //
        //        foundStrings shouldEqual expectedMatches
    }

    it should "properly dump and load relevant settings" in {
        //        val indexFile = new File( tmpFolder, "index2" )
        //        val customConfig : Config = {
        //            testConfig
        //              // re-compute the index and docs path's
        //              .withValue( "odinson.indexDir", ConfigValueFactory.fromAnyRef( indexFile.getAbsolutePath ) )
        //              .withValue(
        //                  "odinson.index.storedFields",
        //                  ConfigValueFactory.fromAnyRef( Seq( "apple", "banana", "kiwi", "raw" ).asJava )
        //                  )
        //        }
        //
        //        val indexWriter = OdinsonIndexWriter.fromConfig( customConfig )
        //        // close and write the settings file
        //        indexWriter.close()
        //        val settings = IndexSettings.fromDirectory( FSDirectory.open( indexFile.toPath ) )
        //        settings.storedFields should contain theSameElementsAs Seq(
        //            "apple",
        //            "banana",
        //            "kiwi",
        //            indexWriter.displayField
        //            )
        //        deleteIndex
    }

    it should "store stored fields and not others" in {
        //        val doc = getDocument( "rainbows" )
        //        val customConfig : Config = defaultConfig
        //          .withValue(
        //              "odinson.index.storedFields",
        //              ConfigValueFactory.fromAnyRef( Seq( "tag", "raw" ).asJava )
        //              )
        //
        //        def ee = mkExtractorEngine( customConfig, doc )
        //
        //        // we asked it to store `tag` so the extractor engine should be able to access the content
        //        ee.getTokensForSpan( 0, "tag", 0, 1 ) should contain only "NNS"
        //        // though `entity` is a field in the Document, it wasn't stored, so the extractor engine shouldn't
        //        // be able to retrieve the content
        //        an[ OdinsonException ] should be thrownBy ee.getTokensForSpan( 0, "entity", 0, 1 )

    }

    it should "throw an exception if the displayField isn't in the storedFields" in {
        //        val indexFile = new File( tmpFolder, "index2" )
        //        val customConfig : Config = {
        //            testConfig
        //              // re-compute the index and docs path's
        //              .withValue( "odinson.indexDir", ConfigValueFactory.fromAnyRef( indexFile.getAbsolutePath ) )
        //              .withValue(
        //                  "odinson.index.storedFields",
        //                  ConfigValueFactory.fromAnyRef( Seq( "apple", "banana", "kiwi" ).asJava )
        //                  )
        //        }
        //
        //        an[ OdinsonException ] shouldBe thrownBy {
        //            OdinsonIndexWriter.fromConfig( customConfig )
        //        }
    }

    it should "store and retrieve large graphs" in {
        //        val numTokens = 4000
        //        val large = Array.fill[ String ]( numTokens )( "test" )
        //        large( 0 ) = "start"
        //        large( 3 ) = "success"
        //        val raw = TokensField( "raw", large.toSeq )
        //        val word = TokensField( "word", large.toSeq )
        //
        //        val deps = new Array[ (Int, Int, String) ]( numTokens )
        //        for ( i <- 0 until numTokens - 1 ) {
        //            if ( i < 3 ) {
        //                // (0,1,edge)
        //                deps( i ) = (i, i + 1, "edge")
        //            } else {
        //                deps( i ) = (i, i + 1, "nomatch")
        //            }
        //        }
        //        val roots = Set[ Int ]( 0 )
        //        val graph = GraphField( "dependencies", deps, roots )
        //
        //        val sent = Sentence( numTokens, Seq( raw, word, graph ) )
        //        val doc = Document( "testdoc", Seq.empty, Seq( sent ) )
        //
        //        // Yes, in fact the deps field is above the previous threshold
        //        val incomingEdges = graph.mkIncomingEdges( numTokens )
        //        val outgoingEdges = graph.mkOutgoingEdges( numTokens )
        //        val indexWriter = setupOdinsonIndex
        //
        //        val directedGraph = indexWriter.mkDirectedGraph( incomingEdges, outgoingEdges, roots.toArray )
        //        val bytes = UnsafeSerializer.graphToBytes( directedGraph )
        //        // previously, we only supported up to this: sortedDocValuesFieldMaxSize = 32766
        //        bytes.length should be > ( 32766 )
        //        indexWriter.close()
        //
        //        // ensure the EE can use it
        //        val ee =
        //            extractorEngineWithConfigValue( doc, "odinson.index.maxNumberOfTokensPerSentence", numTokens )
        //        val rules =
        //            """
        //              |rules:
        //              |  - name: testrule
        //              |    type: basic
        //              |    label: Test
        //              |    pattern: |
        //              |      start >edge+ success
        //              |""".stripMargin
        //        val extractors = ee.compileRuleString( rules )
        //        val results = ee.extractNoState( extractors ).toArray
        //        results.length should be( 1 )
        //        ee.getTokensForSpan( results.head ) should contain only "success"
    }

}