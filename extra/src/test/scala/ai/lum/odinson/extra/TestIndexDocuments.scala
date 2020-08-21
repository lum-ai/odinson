package ai.lum.odinson.extra

import org.scalatest._
import ai.lum.common.ConfigFactory
import com.typesafe.config.{Config, ConfigValueFactory}
import ai.lum.odinson.{Document, ExtractorEngine, Field, OdinsonIndexWriter, StringField}

import scala.reflect.io.Directory
import java.io.File

import org.apache.lucene.index.IndexWriter

// TODO: can I also extend basespec from here?
class TestIndexDocuments extends FlatSpec with Matchers {
  // get the resources folder
  val resourcesFolder = getClass.getResource("/").getFile
  // 
  def deleteIndex = {
    val dir = new Directory(new File(resourcesFolder + "index"))
    dir.deleteRecursively()
  }

  def eeWithNewIndex: ExtractorEngine = {
    // delete index if it already exists
    deleteIndex
    // run stuff
    IndexDocuments.main(Array(resourcesFolder))
    // get config and inject required values
    var config = ConfigFactory.load()
    config = config
      .withValue("odinson.dataDir", ConfigValueFactory.fromAnyRef(resourcesFolder))
      // re-compute the index and docs paths
      .withValue(
        "odinson.indexDir",
        ConfigValueFactory.fromAnyRef(resourcesFolder + "/index")
      )
      .withValue(
        "odinson.docsDir",
        ConfigValueFactory.fromAnyRef(resourcesFolder + "/docs")
      )
    // get an ee
    ExtractorEngine.fromConfig(config.getConfig("odinson"))
  }
  val ee = eeWithNewIndex

  // make sure the function that reads the files work when pointing to the resources
  "IndexDocuments" should "get the correct list of files" in {
  // make sure the files are there
    ee.numDocs shouldEqual (2201)
  }


  it should "replace invalid characters prior to indexing to prevent off-by-one errors" in {
    val pattern = "complex <nsubj phosphorylate >dobj []"
    val expectedMatches = Array("AKT1")
    val query = ee.compiler.mkQuery(pattern)
    val results = ee.query(query, 1)
    results.totalHits should equal (1)
    val matches = results.scoreDocs.head.matches
    val doc = results.scoreDocs.head.doc
    val foundStrings = matches.map(m => ee.getString(doc, m))
    foundStrings shouldEqual expectedMatches
  }
}
