package ai.lum.odinson.extra

import java.nio.file.Files

import ai.lum.odinson.Document
import ai.lum.odinson.utils.exceptions.OdinsonException
import org.scalatest._
import java.io.{ File, IOException }
import org.apache.commons.io.FileUtils

import scala.reflect.io.Directory

class TestAnnotateDocuments extends FlatSpec with Matchers {

  // get the resources directory and the text directory where the text to annotate is stored
  val resourcesFolder = getClass.getResource("/").getFile
  val srcTextDir = new File(resourcesFolder, "text")

  // create the temporary test directory
  val tmpFolder = Files.createTempDirectory("odinson-test").toFile().getAbsolutePath

  // create directories and files that will be used during the test
  val dataDir = tmpFolder
  val textDir = new File(dataDir, "text")
  val docsDir = new File(tmpFolder, "docs")
  val serializedTestFile = new File(docsDir, "test-text.json.gz")

  // copy the text to annotate into the temporary test directory
  try {
    FileUtils.copyDirectory(srcTextDir, textDir)
  } catch {
    case e: IOException =>
      throw new OdinsonException(s"Can't copy text directory ${srcTextDir}")
  }

  def deleteDocs = {
    val dir = new Directory(docsDir)
    dir.deleteRecursively()
  }

  "AnnotateDocuments" should "get the correct annotated, deserializeable Document file when processing with FastNLPProcessor" in {

    //delete docs if already exists
    deleteDocs

    // run the annotation
    AnnotateText.main(Array(tmpFolder, "FastNLPProcessor"))

    docsDir.listFiles() should contain(serializedTestFile)

    val deserialized = Document.fromJson(serializedTestFile)

    deserialized.getClass.toString shouldEqual "class ai.lum.odinson.Document"

  }

  "AnnotateDocuments" should "get the correct annotated, deserializeable Document file when processing with CluProcessor" in {

    //delete docs if already exists
    deleteDocs

    // run the annotation
    AnnotateText.main(Array(tmpFolder, "CluProcessor"))

    docsDir.listFiles() should contain(serializedTestFile)

    val deserialized = Document.fromJson(serializedTestFile)

    deserialized.getClass.toString shouldEqual "class ai.lum.odinson.Document"

  }
}
