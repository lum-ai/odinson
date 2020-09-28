package ai.lum.odinson.extra

import org.scalatest._
//import ai.lum.common.ConfigFactory
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import ai.lum.odinson.ExtractorEngine

import scala.reflect.io.Directory
import java.io.{File, IOException}

import org.apache.commons.io.FileUtils
import org.apache.commons.io.FileUtils._

// TODO: can I also extend basespec from here?
class TestIndexDocuments extends FlatSpec with Matchers {

//  val defaultconfig = ConfigFactory.load()
//
//  val testConfig = {
//    defaultconfig.withValue()
//  }
  // get the resources folder
  val resourcesFolder = getClass.getResource("/").getFile
  println(resourcesFolder)


  val tmpFolder = "/tmp/"

  val srcDir = new File(resourcesFolder)
  val destDir = new File(tmpFolder)
  //
    try {
      FileUtils.copyDirectory(srcDir, destDir);
    } catch {
      case e: IOException =>
        println("Can't copy resources directory")
    }

  //
  def deleteIndex = {
    val dir = new Directory(new File(tmpFolder + "index"))
    println("Deleting dir: " + dir)
    dir.deleteRecursively()
  }
  // make sure the function that reads the files work when pointing to the resources
  "IndexDocuments" should "get the correct list of files" in {
    // delete index if it already exists
    deleteIndex
    // run stuff
    IndexDocuments.main(Array(tmpFolder))
    // get config and ingect required values
    var config = ConfigFactory.load()
    config = config
      .withValue("odinson.dataDir", ConfigValueFactory.fromAnyRef(tmpFolder))
      // re-compute the index and docs path's
      .withValue(
        "odinson.indexDir",
        ConfigValueFactory.fromAnyRef(tmpFolder + "/index")
      )
      .withValue(
        "odinson.docsDir",
        ConfigValueFactory.fromAnyRef(tmpFolder + "/docs")
      )

//    println("conf odinson path: " + config.atKey("odinson"))
    // get an ee
    val ee = ExtractorEngine.fromConfig(config.getConfig("odinson"))


    // make sure the files are there
    // There are two files, one with 150 sentences + 1 parent doc, and one
    // with 100 sentences + 1 parent doc = 252 docs
    ee.numDocs shouldEqual (252)
  }
}
