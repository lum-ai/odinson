package ai.lum.odinson.lucene.index

import ai.lum.odinson.test.utils.OdinsonTest
import ai.lum.odinson.utils.IndexSettings
import ai.lum.odinson.utils.exceptions.OdinsonException
import com.typesafe.config.{ Config, ConfigValueFactory }
import org.apache.lucene.store.FSDirectory
import org.scalatest.BeforeAndAfterEach

import java.io.File
import scala.collection.JavaConverters._
import scala.reflect.io.Directory

class TestIncrementalIndex extends OdinsonTest with BeforeAndAfterEach {
  type Fixture = IncrementalOdinsonIndex

  val testIndexDir: File = {
    val file = new File("./target/incremental_index_test")
    file.mkdirs()
    file
  }

  val testConfig: Config = {
    defaultConfig
      .withValue("odinson.indexDir", ConfigValueFactory.fromAnyRef(testIndexDir.getCanonicalPath))
      .withValue("odinson.index.incremental", ConfigValueFactory.fromAnyRef(true))
      .withValue("odinson.index.refreshMs", ConfigValueFactory.fromAnyRef(-1))
  }

  override def afterEach(): Unit = {
    val dir = new Directory(testIndexDir)
    dir.deleteRecursively()
  }

  "IncrementalOdinsonIndex" should "properly export and load settings" in {
    val customConfig: Config = testConfig.withValue(
      "odinson.index.storedFields",
      ConfigValueFactory.fromAnyRef(Seq("apple", "banana", "kiwi", "raw").asJava)
    )
    val index = OdinsonIndex.fromConfig(customConfig)
    index.close()

    val settings = IndexSettings.fromDirectory(FSDirectory.open(testIndexDir.toPath))
    settings.storedFields should contain theSameElementsAs Seq(
      "apple",
      "banana",
      "kiwi",
      index.displayField
    )
  }

  "Odinson Incremental Index" should "should return an index from config" in {
    val index = OdinsonIndex.fromConfig(testConfig)
    index.directory.listAll.head shouldBe "write.lock"
    index.close()
  }

  // this is now handled in the OdinsonIndex class
  it should "properly dump and load relevant settings" in {
    val config = {
      testConfig
        .withValue(
          "odinson.index.storedFields",
          ConfigValueFactory.fromAnyRef(Seq("apple", "banana", "kiwi", "raw").asJava)
        )
    }
    val index = OdinsonIndex.fromConfig(config)
    index.close()
    val settings = IndexSettings.fromDirectory(index.directory)
    settings.storedFields should contain theSameElementsAs Seq(
      "apple",
      "banana",
      "kiwi",
      index.displayField
    )
  }

  it should "throw an exception if the displayField isn't in the storedFields" in {
    val config: Config = {
      testConfig
        .withValue(
          "odinson.index.storedFields",
          ConfigValueFactory.fromAnyRef(Seq("apple", "banana", "kiwi").asJava)
        )
    }

    an[OdinsonException] shouldBe thrownBy {
      OdinsonIndex.fromConfig(config)
    }
  }

  it should "incrementally add Odinson Documents to an open index" in {
    val index = OdinsonIndex.fromConfig(testConfig)

    val aliens = getDocument("alien-species")
    index.indexOdinsonDoc(aliens)

    index.numDocs() shouldBe 2 // # of lucenen docs

    val gummyBears = getDocument("gummy-bears-consumption")
    index.indexOdinsonDoc(gummyBears)

    index.numDocs() shouldBe 4 // # number of lucene docs
    index.close()
  }

  it should "incrementally write Odinson Documents to a previously closed index" in {
    var index = OdinsonIndex.fromConfig(testConfig)

    val aliens = getDocument("alien-species")
    index.indexOdinsonDoc(aliens)

    index.numDocs() shouldBe 2 // # of lucene docs
    index.close()

    index = OdinsonIndex.fromConfig(testConfig)
    val gummyBears = getDocument("gummy-bears-consumption")
    index.indexOdinsonDoc(gummyBears)

    index.numDocs() shouldBe 4 // # number of lucene docs
    index.close()
  }

}
