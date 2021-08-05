package ai.lum.odinson.lucene

import ai.lum.odinson.lucene.index.{IncrementalOdinsonIndex, OdinsonIndex}
import ai.lum.odinson.utils.IndexSettings
import ai.lum.odinson.utils.TestUtils.OdinsonTest
import ai.lum.odinson.utils.exceptions.OdinsonException
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

    "Odinson Incremental Index" should "object should return index from config correctly" in {
        val index = createOdinsonIndex( testConfig )
        index.directory.listAll.head shouldBe "write.lock"
        index.close()
    }

    override def afterAll( ) : Unit = {
        val dir = new Directory( testDataDir )
        dir.deleteRecursively()
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

    it should "throw an exception if the displayField isn't in the storedFields" ignore {
        val config : Config = {
            testConfig
              .withValue( "odinson.index.storedFields", ConfigValueFactory.fromAnyRef( Seq( "apple", "banana", "kiwi" ).asJava ) )
        }

        an[ OdinsonException ] shouldBe thrownBy {
            OdinsonIndex.fromConfig( config )
        }
    }

    it should "incrementally add Odinson Documents to an open index" in {
        val index = createOdinsonIndex( testConfig )

        val aliens = getDocument( "alien-species" )
        index.addOdinsonDocument( aliens )

        index.numDocs() shouldBe 2 // # of lucenen docs

        val gummyBears = getDocument( "gummy-bears-consumption" )
        index.addOdinsonDocument( gummyBears )

        index.numDocs() shouldBe 4 // # number of lucene docs
    }

    it should "incrementally write Odinson Documents to a previously closed index" in {

    }

    private def createOdinsonIndex( config : Config ) : OdinsonIndex = {
        OdinsonIndex.fromConfig( config )
    }

}
