package ai.lum.odinson.lucene

import ai.lum.odinson.lucene.index.{IncrementalOdinsonIndex, OdinsonIndex}
import ai.lum.odinson.utils.IndexSettings
import ai.lum.odinson.utils.TestUtils.OdinsonTest
import com.typesafe.config.{Config, ConfigValueFactory}
import org.scalatest.BeforeAndAfterAll

import java.io.File
import scala.collection.JavaConverters._
import scala.reflect.io.Directory

class TestIncrementalIndex extends OdinsonTest with BeforeAndAfterAll {
    type Fixture = IncrementalOdinsonIndex

    val testDataDir : File = {
        val file = new File( "./target/incremental_index_test" )
        file.mkdirs()
        file
    }

    val testConfig : Config = {
        defaultConfig
          .withValue( "odinson.indexDir", ConfigValueFactory.fromAnyRef( testDataDir.getCanonicalPath ) )
          .withValue( "odinson.index.incremental", ConfigValueFactory.fromAnyRef( true ) )
    }

    "OdinsonIndexWriter" should "object should return index from config correctly" in {
        //        val indexDir = mkTestIndexDir()
        //        val config = testConfig.withValue( "odinson.indexDir", ConfigValueFactory.fromAnyRef( indexDir.getAbsolutePath ) )
        val index = createOdinsonIndex( testConfig )
        index.directory.listAll.head shouldBe "write.lock"
        index.close()
    }

    override def afterAll( ) : Unit = {
        val dir = new Directory( testDataDir )
        dir.deleteRecursively()
    }


    it should "replace invalid characters prior to indexing to prevent off-by-one errors" ignore {
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
        val config = {
            testConfig
              .withValue( "odinson.index.storedFields", ConfigValueFactory.fromAnyRef( Seq( "apple", "banana", "kiwi", "raw" ).asJava ) )
        }
        val index = createOdinsonIndex( config )
        index.close()
        val settings = IndexSettings.fromDirectory( index.directory )
        settings.storedFields should contain theSameElementsAs Seq( "apple", "banana", "kiwi", index.displayField )
    }

    it should "store stored fields and not others" ignore {
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

    it should "throw an exception if the displayField isn't in the storedFields" ignore {
        //        val indexFile = new File( tmpFolder, "index2" )
        //        val customConfig : Config = {
        //            testConfig
        //              // re-compute the index and docs path's
        //              .withValue( "odinson.tmpFolder", ConfigValueFactory.fromAnyRef( indexFile.getAbsolutePath ) )
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

    it should "store and retrieve large graphs" ignore {
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
        //        val indexWriter = createOdinsonIndex
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

    it should "incrementally write to an index that remains open" in {
        val index = createOdinsonIndex( testConfig )

        val aliens = getDocument( "alien-species" )
        index.addOdinsonDocument( aliens )

        println( index.numDocs() )

        val gummyBears = getDocument( "gummy-bears-consumption" )
        index.addOdinsonDocument( gummyBears )

        println( index.numDocs() )
    }

    it should "incrementally write to an index that has been previously closed" in {

    }

    private def createOdinsonIndex( config : Config ) : OdinsonIndex = {
        OdinsonIndex.fromConfig( config )
    }

}