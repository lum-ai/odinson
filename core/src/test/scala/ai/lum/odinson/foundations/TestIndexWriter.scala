package ai.lum.odinson.foundations

// test imports
import java.nio.file.Files

import com.typesafe.config.{Config, ConfigValueFactory}
import org.scalatest._
// lum imports
import ai.lum.odinson.{OdinsonIndexWriter, BaseSpec, DateField, StringField}
import ai.lum.common.ConfigFactory
// file imports
import scala.reflect.io.Directory
import java.io.File

class TestOdinsonIndexWriter extends BaseSpec {
  type Fixture = OdinsonIndexWriter

  val tmpFolder: File = Files.createTempDirectory("odinson-test").toFile()
  val indexDir = new File(tmpFolder, "index")


  val defaultConfig = ConfigFactory.load()
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
    OdinsonIndexWriter.fromConfig(testConfig.getConfig("odinson"))
  }
  
  "OdinsonIndexWriter" should "object should return index from config correctly" in {
    // get index writer
    val indexWriter = getOdinsonIndexWriter
    // get the directory
    val directory = indexWriter.directory
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
    def ee = Utils.mkExtractorEngine(doc)

    val pattern = "complex <nsubj phosphorylate >dobj []"
    val expectedMatches = Array("AKT1")

    val query = ee.compiler.mkQuery(pattern)
    val results = ee.query(query, 1)
    results.totalHits should equal (1)

    val matches = results.scoreDocs.head.matches
    val docId = results.scoreDocs.head.doc
    val foundStrings = matches.map(m => ee.getStringForSpan(docId, m))

    foundStrings shouldEqual expectedMatches
  }
}
