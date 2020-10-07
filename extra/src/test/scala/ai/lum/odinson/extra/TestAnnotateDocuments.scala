package ai.lum.odinson.extra

import java.nio.file.Files

import ai.lum.odinson.utils.exceptions.OdinsonException
import org.scalatest._
//import ai.lum.common.ConfigFactory
import java.io.{File, IOException}

import ai.lum.odinson.ExtractorEngine
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.apache.commons.io.FileUtils

import scala.reflect.io.Directory


class TestAnnotateDocuments extends FlatSpec with Matchers {

  // get the resources folder and the text directory where the text to annotate is stored
  val resourcesFolder = getClass.getResource("/").getFile
//  val srcDir = new File(resourcesFolder)
  val srcTextDir = new File(resourcesFolder, "text")

  // create the temporary test directory
  val tmpFolder = Files.createTempDirectory("odinson-test").toFile().getAbsolutePath


  val dataDir = tmpFolder
  val textDir =  new File(tmpFolder, "text")
  val docsDir = new File(tmpFolder, "docs")

  // copy the text to annotate into the temporary test directory
  try {
      FileUtils.copyDirectory(srcTextDir, new File(dataDir, "text"));
    } catch {
      case e: IOException =>
        throw new OdinsonException("Can't copy text directory")
    }


  def deleteDocs = {
    val dir = new Directory(docsDir)
    dir.deleteRecursively()
  }


  "AnnotateDocuments" should "get the correct annotated file" in {

    //delete docs if already exists
    deleteDocs

    // run the annotation
    AnnotateText.main(Array(tmpFolder))

    docsDir.listFiles() should contain (new File(docsDir, "test-text.json.gz"))

  }
}
