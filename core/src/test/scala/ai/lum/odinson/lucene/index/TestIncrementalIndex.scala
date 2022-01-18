package ai.lum.odinson.lucene.index

import ai.lum.odinson.metadata.MetadataCompiler
import ai.lum.odinson.test.utils.OdinsonTest
import ai.lum.odinson.utils.IndexSettings
import ai.lum.odinson.utils.exceptions.OdinsonException
import ai.lum.odinson.{ ExtractorEngine, TokensField }
import com.typesafe.config.{ Config, ConfigValueFactory }
import org.apache.lucene.store.FSDirectory
import org.scalatest.BeforeAndAfterEach

import java.io.File
import java.nio.file.Files
import scala.collection.JavaConverters._
import scala.reflect.io.Directory

class TestIncrementalIndex extends OdinsonTest with BeforeAndAfterEach {
  type Fixture = IncrementalOdinsonIndex

  val testIndexDir: File = {
    val tmpDir: File = Files.createTempDirectory("odinson-test").toFile
    tmpDir.mkdirs()
    tmpDir
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
    OdinsonIndex.usingIndex(testConfig) { index =>
      val aliens = getDocument("alien-species")
      index.indexOdinsonDoc(aliens)

      index.numDocs() shouldBe 2 // # of lucenen docs

      val gummyBears = getDocument("gummy-bears-consumption")
      index.indexOdinsonDoc(gummyBears)

      index.numDocs() shouldBe 4 // # number of lucene docs
    }
  }

  it should "incrementally write Odinson Documents to a previously closed index" in {
    OdinsonIndex.usingIndex(testConfig) { index =>
      val aliens = getDocument("alien-species")
      index.indexOdinsonDoc(aliens)

      index.numDocs() shouldBe 2 // # of lucene docs
    }

    OdinsonIndex.usingIndex(testConfig) { index =>
      val gummyBears = getDocument("gummy-bears-consumption")
      index.indexOdinsonDoc(gummyBears)

      index.numDocs() shouldBe 4 // # number of lucene docs
    }
  }

  it should "incrementally delete Odinson Documents from an open index" in {
    // doc w/ 1 sentence & metadata w/ 2 sets of nested fields
    val pies = getDocument("tp-pies")
    OdinsonIndex.usingIndex(testConfig) { index =>
      index.indexOdinsonDoc(pies)

      val odinsonDocId = pies.id
      index.numDocs() shouldBe 4 // # of lucene docs
      index.luceneDocIdsFor(odinsonDocId).size shouldBe 4

      // Deleting an Odinson document should delete all of its sentences, as well as its metadata (including any nested fields)
      index.deleteOdinsonDoc(odinsonDocId)

      index.numDocs() shouldBe 0
      index.luceneDocIdsFor(odinsonDocId).size shouldBe 0
    }
  }

  it should "incrementally delete Odinson Documents from a previously closed index" in {
    // doc w/ 1 sentence & metadata w/ 2 sets of nested fields
    val pies = getDocument("tp-pies")
    OdinsonIndex.usingIndex(testConfig) { index =>
      index.indexOdinsonDoc(pies)

      index.numDocs() shouldBe 4 // # of lucene docs
    }
    OdinsonIndex.usingIndex(testConfig) { index =>
      index.numDocs() shouldBe 4 // # of lucene docs
      index.luceneDocIdsFor(pies.id).size shouldBe 4

      // Deleting an Odinson document should delete all of its sentences, as well as its metadata (including any nested fields)
      index.deleteOdinsonDoc(pies.id)

      index.numDocs() shouldBe 0
      index.luceneDocIdsFor(pies.id).size shouldBe 0
    }
  }

  it should "incrementally delete an Odinson Document from a previously closed index containing multiple documents" in {
    // doc w/ 1 sentence & metadata w/ 2 sets of nested fields
    // we'll index and delete this one
    val pies = getDocument("tp-pies")
    // we'll index this one, but not delete it
    val briggs = getDocument("tp-briggs")
    OdinsonIndex.usingIndex(testConfig) { index =>
      index.indexOdinsonDoc(pies)
      index.numDocs() shouldBe 4 // # of lucene docs
      index.indexOdinsonDoc(briggs)
      index.numDocs() should be > 4
    }

    OdinsonIndex.usingIndex(testConfig) { index =>
      val totalDocs = index.numDocs()
      totalDocs should be > 4
      index.luceneDocIdsFor(pies.id).size shouldBe 4
      // Deleting an Odinson document should delete all of its sentences, as well as its metadata (including any nested fields)
      index.deleteOdinsonDoc(pies.id)

      val remaining = totalDocs - 4
      index.numDocs() shouldBe remaining
      index.luceneDocIdsFor(pies.id).size shouldBe 0
    }
  }

  it should "not crash if asked to incrementally delete a non-existent Odinson Document" in {
    OdinsonIndex.usingIndex(testConfig) { index =>
      // the index is empty
      index.numDocs() shouldBe 0
      // while no such doc exists,
      // this should not cause an error
      noException should be thrownBy index.deleteOdinsonDoc("tp-pies")
    }
  }

  it should "update an index with a new version of an Odinson Document (respecting changes to sentences)" in {
    // index doc and then update with an abbreviated version
    ExtractorEngine.usingEngine(testConfig) { engine =>
      // we'll index this doc and later update
      val majorBriggs = getDocument("tp-briggs")
      val index = engine.index
      // the index is empty
      index.numDocs() shouldBe 0
      index.indexOdinsonDoc(majorBriggs)
      val oldCount = index.numDocs()
      // let's remove some sentences and update the index
      val minorBriggs = majorBriggs.copy(sentences = Seq(majorBriggs.sentences.head))
      index.updateOdinsonDoc(minorBriggs)
      index.numDocs() should be < oldCount
    }
  }

  it should "update an index with a new version of an Odinson Document (respecting changes to metadata)" in {
    // index doc and then update with additional metadata
    ExtractorEngine.usingEngine(testConfig) { engine =>
      // "This must be where pies go to die."
      // we'll index this doc and later update
      val pies = getDocument("tp-pies")
      val metadataFilter = MetadataCompiler.mkQuery("flavor contains 'cherry'")
      val query = engine.mkFilteredQuery("[lemma=pie]", metadataFilter)
      val index = engine.index
      // the index is empty
      index.numDocs() shouldBe 0
      index.indexOdinsonDoc(pies)
      // the query shouldn't match
      engine.query(query).scoreDocs.length shouldBe 0

      // let's replace that doc with one that will match the query
      val flavorField = TokensField(name = "flavor", tokens = Seq("cherry"))
      val cherryPies = pies.copy(metadata = pies.metadata ++ Seq(flavorField))
      index.updateOdinsonDoc(cherryPies)
      // the query should now match
      val res = engine.query(query)
      res.scoreDocs.length shouldBe 1
    }
  }

  it should "not crash if asked to update a non-existent Odinson Document" in {
    ExtractorEngine.usingEngine(testConfig) { engine =>
      val pies = getDocument("tp-pies")
      val index = engine.index
      // the index is empty
      index.numDocs() shouldBe 0
      // while no such doc exists,
      // this should not cause an error
      noException should be thrownBy index.updateOdinsonDoc(pies)
    }
  }
}
