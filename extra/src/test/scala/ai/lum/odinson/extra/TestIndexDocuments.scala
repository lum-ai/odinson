package ai.lum.odinson.extra

import java.nio.file.Files

import ai.lum.odinson.utils.exceptions.OdinsonException
import org.apache.lucene.store.FSDirectory
import org.scalatest._
import org.apache.lucene.store.IOContext

import scala.reflect.io.Directory
import scala.util.parsing.json.JSON
//import ai.lum.common.ConfigFactory
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import ai.lum.odinson.ExtractorEngine

import java.io.{File, IOException}

import org.apache.commons.io.FileUtils
import org.apache.commons.io.FileUtils._

// TODO: can I also extend basespec from here?
class TestIndexDocuments extends FlatSpec with Matchers {

  // get the resources folder
  val resourcesFolder = getClass.getResource("/").getFile

  val tmpFolder = Files.createTempDirectory("odinson-test").toFile()

  val srcDir = new File(resourcesFolder)


    try {
      FileUtils.copyDirectory(srcDir, tmpFolder);
    } catch {
      case e: IOException =>
        throw new OdinsonException("Can't copy resources directory")
    }

  val dataDir = tmpFolder.getAbsolutePath
  val indexDir =  new File(tmpFolder, "index")
  val docsDir = new File(tmpFolder, "docs").getAbsolutePath


  def deleteIndex = {
    val dir = new Directory(indexDir)
    dir.deleteRecursively()
  }

  // make sure the function that reads the files work when pointing to the resources
  "IndexDocuments" should "get the correct list of files" in {
    // delete index if it already exists
    deleteIndex
    // run stuff
    IndexDocuments.main(Array(tmpFolder.getAbsolutePath))
    // get config and ingect required values
    var config = ConfigFactory.load()
    config = config
      .withValue("odinson.dataDir", ConfigValueFactory.fromAnyRef(dataDir))
      // re-compute the index and docs path's
      .withValue(
      "odinson.indexDir",
      ConfigValueFactory.fromAnyRef(indexDir.getAbsolutePath)
    )
      .withValue(
        "odinson.docsDir",
        ConfigValueFactory.fromAnyRef(docsDir)
      )

    // get an ee
    val ee = ExtractorEngine.fromConfig(config)


    // make sure the files are there
    // There are two files, one with 150 sentences + 1 parent doc, and one
    // with 100 sentences + 1 parent doc = 252 docs
    ee.numDocs shouldEqual (252)
  }

    it should "contain the appropriate meta files" in {

      val buildInfoFileName = "buildinfo.json"
      val buildInfoJsonFile = new File(indexDir, buildInfoFileName)
      val dependenciesFile = new File(indexDir, "dependencies.txt")


      indexDir.listFiles() should contain (dependenciesFile)
      indexDir.listFiles() should contain (buildInfoJsonFile)


      def jsonToMap(directory: File, jsonFileName: String): Map[String, Any] = {
        // takes a json file and returns a map
        // fixme: may need recursion for nested maps
        val dir = FSDirectory.open(directory.toPath)
        val inputStream = dir.openInput(jsonFileName, new IOContext).readString()
        val jsonAsMap = JSON.parseFull(inputStream).get.asInstanceOf[Map[String, Any]]
        jsonAsMap
      }


      val buildInfoJson = jsonToMap(indexDir, buildInfoFileName)
      buildInfoJson.keys should contain ("version")

    }

}
