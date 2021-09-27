//package ai.lum.odinson.lucene
//
//import ai.lum.odinson.lucene.index.{OdinsonIndex, WriteOnceOdinsonIndex}
//import ai.lum.odinson.test.utils.OdinsonTest
//import ai.lum.odinson.utils.IndexSettings
//import ai.lum.odinson.utils.exceptions.OdinsonException
//import com.typesafe.config.{Config, ConfigValueFactory}
//import org.apache.lucene.store.FSDirectory
//import org.scalatest.BeforeAndAfterEach
//
//import java.io.File
//import scala.collection.JavaConverters._
//import scala.reflect.io.Directory
//
//class TestWriteOnceIndex extends OdinsonTest with BeforeAndAfterEach {
//    type Fixture = WriteOnceOdinsonIndex
//
//    val testIndexDir : File = {
//        val file = new File( "./target/write_once_index_test" )
//        file.mkdirs()
//        file
//    }
//
//    val testConfig : Config = {
//        defaultConfig
//          .withValue( "odinson.indexDir", ConfigValueFactory.fromAnyRef( testIndexDir.getCanonicalPath ) )
//    }
//
//    override def beforeEach( ) : Unit = {
//        val dir = new Directory( testIndexDir )
//        dir.deleteRecursively()
//    }
//
//    override def afterEach( ) : Unit = {
//        val dir = new Directory( testIndexDir )
//        dir.deleteRecursively()
//    }
//
//    it should "properly export and load settings" in {
//        val customConfig : Config = testConfig.withValue( "odinson.index.storedFields", ConfigValueFactory.fromAnyRef( Seq( "apple", "banana", "kiwi", "raw" ).asJava ) )
//        val index = OdinsonIndex.fromConfig( customConfig )
//        index.close()
//
//        val settings = IndexSettings.fromDirectory( FSDirectory.open( testIndexDir.toPath ) )
//        settings.storedFields should contain theSameElementsAs Seq( "apple", "banana", "kiwi", index.displayField )
//    }
//
//
//    "Odinson Incremental Index" should "should return an index from config" in {
//        val index = OdinsonIndex.fromConfig( testConfig )
//        index.directory.listAll.head shouldBe "write.lock"
//        index.close()
//    }
//
//    // this is now handled in the OdinsonIndex class
//    it should "properly dump and load relevant settings" in {
//        val customConfig = {
//            testConfig
//              .withValue( "odinson.index.storedFields", ConfigValueFactory.fromAnyRef( Seq( "apple", "banana", "kiwi", "raw" ).asJava ) )
//        }
//        val index = OdinsonIndex.fromConfig( customConfig )
//        index.close()
//        val settings = IndexSettings.fromDirectory( index.directory )
//        settings.storedFields should contain theSameElementsAs Seq( "apple", "banana", "kiwi", index.displayField )
//    }
//
//    it should "throw an exception if the displayField isn't in the storedFields" in {
//        val customConfig : Config = {
//            testConfig
//              .withValue( "odinson.index.storedFields", ConfigValueFactory.fromAnyRef( Seq( "apple", "banana", "kiwi" ).asJava ) )
//        }
//
//        an[ OdinsonException ] shouldBe thrownBy {
//            OdinsonIndex.fromConfig( customConfig )
//        }
//    }
//
//    it should "add Odinson documents to the index" in {
//        var index = OdinsonIndex.fromConfig( testConfig )
//
//        // writes are not committed until the end, during the close() method
//        val aliens = getDocument( "alien-species" )
//        index.indexOdinsonDoc( aliens )
//        index.close()
//
//        // check that index is persistent and can be read again
//        index = OdinsonIndex.fromConfig( testConfig )
//        index.numDocs() shouldBe 2
//    }
//
//}
