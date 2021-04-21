package ai.lum.odinson.foundations

// test imports
import java.nio.file.Files

import ai.lum.odinson.lucene.search.OdinsonIndexSearcher
import ai.lum.odinson.utils.IndexSettings
import ai.lum.odinson.utils.TestUtils.OdinsonTest
import ai.lum.odinson.utils.exceptions.OdinsonException
import com.typesafe.config.{Config, ConfigValueFactory}
import org.apache.lucene.document.{BinaryDocValuesField, Document}
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.BytesRef

import scala.collection.JavaConverters.asJavaIterableConverter
// lum imports
import ai.lum.odinson.{DateField, OdinsonIndexWriter, StringField}
// file imports
import java.io.File

import scala.reflect.io.Directory

class TestOdinsonIndexWriter extends OdinsonTest {
  type Fixture = OdinsonIndexWriter

  val tmpFolder: File = Files.createTempDirectory("odinson-test").toFile()
  val indexDir = new File(tmpFolder, "index")

  val testConfig: Config = {
    defaultConfig
      // re-compute the index and docs path's
      .withValue(
        "odinson.indexDir",
        ConfigValueFactory.fromAnyRef(indexDir.getAbsolutePath)
      )
  }

  def deleteIndex = {
    val dir = new Directory(indexDir)
    dir.deleteRecursively()
  }

  def getOdinsonIndexWriter: OdinsonIndexWriter = {
    deleteIndex
    OdinsonIndexWriter.fromConfig(testConfig)
  }

  "OdinsonIndexWriter" should "object should return index from config correctly" in {
    // get index writer
    val indexWriter = getOdinsonIndexWriter
    // make sure the folder was created with only the locker inside
    indexWriter.directory.listAll.head should be("write.lock")
    indexWriter.close
  }

  it should "mkLuceneFields should convert Fields to lucene.Fields correctly" in {
    val indexWriter = getOdinsonIndexWriter
    // Initialize fild of type DateField
    var field =
      """{"$type":"ai.lum.odinson.DateField","name":"smth","date":"1993-03-28"}"""
    //
    val dateField = DateField.fromJson(field)
    // DateField
    val luceneDateField = indexWriter.mkLuceneFields(dateField)
    // test
    luceneDateField.head.name shouldEqual ("smth")
    // Initialize field of type StringField
    field =
      """{"$type":"ai.lum.odinson.StringField","name":"smth","string":"foo"}"""
    // StringField
    val stringField = StringField.fromJson(field)
    val luceneStringField = indexWriter.mkLuceneFields(stringField)
    luceneStringField.head.name shouldEqual ("smth")
    // TODO: should we test more stuff
  }

  it should "replace invalid characters prior to indexing to prevent off-by-one errors" in {
    val doc = getDocument("bad-character")
    def ee = mkExtractorEngine(doc)

    val pattern = "complex <nsubj phosphorylate >dobj []"
    val expectedMatches = Array("AKT1")

    val query = ee.compiler.mkQuery(pattern)
    val results = ee.query(query, 1)
    results.totalHits should equal(1)

    val matches = results.scoreDocs.head.matches
    val docId = results.scoreDocs.head.doc
    val foundStrings = matches.map(m => ee.getStringForSpan(docId, m))

    foundStrings shouldEqual expectedMatches
  }

  it should "properly dump and load relevant settings" in {
    val indexFile = new File(tmpFolder, "index2")
    val customConfig: Config = {
      testConfig
        // re-compute the index and docs path's
        .withValue("odinson.indexDir", ConfigValueFactory.fromAnyRef(indexFile.getAbsolutePath))
        .withValue(
          "odinson.index.storedFields",
          ConfigValueFactory.fromAnyRef(Seq("apple", "banana", "kiwi", "raw").asJava)
        )
    }

    val indexWriter = OdinsonIndexWriter.fromConfig(customConfig)
    // close and write the settings file
    indexWriter.close()
    val settings = IndexSettings.fromDirectory(FSDirectory.open(indexFile.toPath))
    settings.storedFields should contain theSameElementsAs Seq(
      "apple",
      "banana",
      "kiwi",
      indexWriter.displayField
    )
  }

  it should "store stored fields and not others" in {

    val doc = getDocument("rainbows")
    val customConfig: Config = defaultConfig
      .withValue(
        "odinson.index.storedFields",
        ConfigValueFactory.fromAnyRef(Seq("tag", "raw").asJava)
      )
    def ee = mkExtractorEngine(customConfig, doc)

    // we asked it to store `tag` so the extractor engine should be able to access the content
    ee.getTokensForSpan(0, "tag", 0, 1) should contain only "NNS"
    // though `entity` is a field in the Document, it wasn't stored, so the extractor engine shouldn't
    // be able to retrieve the content
    an[OdinsonException] should be thrownBy ee.getTokensForSpan(0, "entity", 0, 1)

  }

  it should "throw an exception if the displayField isn't in the storedFields" in {
    val indexFile = new File(tmpFolder, "index2")
    val customConfig: Config = {
      testConfig
        // re-compute the index and docs path's
        .withValue("odinson.indexDir", ConfigValueFactory.fromAnyRef(indexFile.getAbsolutePath))
        .withValue(
          "odinson.index.storedFields",
          ConfigValueFactory.fromAnyRef(Seq("apple", "banana", "kiwi").asJava)
        )
    }

    an[OdinsonException] shouldBe thrownBy { OdinsonIndexWriter.fromConfig(customConfig) }
  }

  it should "store and retrieve large graphs" in {
    // sortedDocValuesFieldMaxSize = 32766

//    val large = Array.fill[Byte](40000)(217.toByte)
//    val indexWriter = getOdinsonIndexWriter
//    val graph = new BinaryDocValuesField("testfield", new BytesRef(large))
//    val doc = new Document
//    doc.add(graph)
//    indexWriter.addDocuments(Seq(doc))
//    indexWriter.commit()
//    indexWriter.close()
//
//    val indexReader = DirectoryReader.open(FSDirectory.open(indexDir.toPath))
//    val computeTotalHits = true
//    val indexSearcher = new OdinsonIndexSearcher(indexReader, computeTotalHits)

  }
}
