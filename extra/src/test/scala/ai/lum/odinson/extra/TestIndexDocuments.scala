package ai.lum.odinson.extra

import org.scalatest._

import ai.lum.common.ConfigFactory
import com.typesafe.config.{Config, ConfigValueFactory}

import ai.lum.odinson.ExtractorEngine

import scala.reflect.io.Directory
import java.io.File

// TODO: can I also extend basespec from here?
class TestIndexDocuments extends FlatSpec with Matchers {
  // get the resources folder
  val resourcesFolder = getClass.getResource("/").getFile
  // 
  def deleteIndex = {
    val dir = new Directory(new File(resourcesFolder + "index"))
    dir.deleteRecursively()
  }
  // make sure the function that reads the files work when pointing to the resources
  "IndexDocuments" should "get the correct list of files" in {
    // delete index if it already exists
    deleteIndex
    // run stuff
    IndexDocuments.main(Array(resourcesFolder))
    // get config and ingect required values
    var config = ConfigFactory.load()
    config = config
      .withValue("odinson.dataDir", ConfigValueFactory.fromAnyRef(resourcesFolder))
      // re-compute the index and docs path's
      .withValue(
        "odinson.indexDir",
        ConfigValueFactory.fromAnyRef(resourcesFolder + "/index")
      )
      .withValue(
        "odinson.docsDir",
        ConfigValueFactory.fromAnyRef(resourcesFolder + "/docs")
      )
    // get an ee
    val ee = ExtractorEngine.fromConfig(config.getConfig("odinson"))
    // make sure the files are there
    ee.numDocs shouldEqual (2199)
  }
}
