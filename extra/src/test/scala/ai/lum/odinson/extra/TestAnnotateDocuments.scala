package ai.lum.odinson.extra

import ai.lum.odinson.Document
import ai.lum.odinson.utils.exceptions.OdinsonException
import org.apache.commons.io.FileUtils
import org.scalatest._

import java.io.File
import java.io.IOException
import java.nio.file.{ Files, Paths }

import scala.reflect.io.Directory

class TestAnnotateDocuments extends FlatSpec with Matchers {

  // get the resources directory and the text directory where the text to annotate is stored
  private val resourcesFolder = getClass.getResource("/").getFile // This ends in /.
  private val srcTextDir = resourcesFolder + "text"

  // create the temporary test directory
  private val tmpFolder = Files.createTempDirectory("odinson-test").toFile.getAbsolutePath

  // create directories and files that will be used during the test
  private val dataDir = tmpFolder
  private val textDir = Paths.get(dataDir, "text")
  private val docsDir = Paths.get(tmpFolder, "docs")
  private val serializedTestFile = docsDir.resolve("test-text.json.gz")
  private val serializedTestChildDir = docsDir.resolve("text-subdir")

  private val serializedTestChildFile =
    serializedTestChildDir.resolve("test-text-subdir-text.json.gz")

  // copy the text to annotate into the temporary test directory
  try {
    FileUtils.copyDirectory(new File(srcTextDir), textDir.toFile)
  } catch {
    case e: IOException =>
      throw OdinsonException(s"Can't copy text directory $srcTextDir")
  }

  private def deleteDocs() = {
    val dir = new Directory(docsDir.toFile)
    dir.deleteRecursively()
  }

  "AnnotateDocuments" should "get the correct annotated, deserializable Document file when processing with FastNLPProcessor" in {

    // delete docs if they already exist
    deleteDocs()

    // run the annotation
    AnnotateText.main(Array(tmpFolder, "FastNLPProcessor"))

    Files.walk(docsDir).toArray should contain(serializedTestFile)
    Files.walk(docsDir).toArray should contain(serializedTestChildDir)
    Files.walk(docsDir).toArray should contain(serializedTestChildFile)

    val deserialized = Document.fromJson(serializedTestFile.toFile)

    deserialized.getClass.toString shouldEqual "class ai.lum.odinson.Document"

    val deserializedChild = Document.fromJson(serializedTestFile.toFile)

    deserializedChild.getClass.toString shouldEqual "class ai.lum.odinson.Document"
  }

  "AnnotateDocuments" should "get the correct annotated, deserializable Document file when processing with CluProcessor" in {

    // delete docs if they already exist
    deleteDocs()

    // run the annotation
    AnnotateText.main(Array(tmpFolder, "CluProcessor"))

    Files.walk(docsDir).toArray should contain(serializedTestFile)
    Files.walk(docsDir).toArray should contain(serializedTestChildDir)
    Files.walk(docsDir).toArray should contain(serializedTestChildFile)

    val deserialized = Document.fromJson(serializedTestFile.toFile)

    deserialized.getClass.toString shouldEqual "class ai.lum.odinson.Document"

    val deserializedChild = Document.fromJson(serializedTestFile.toFile)

    deserializedChild.getClass.toString shouldEqual "class ai.lum.odinson.Document"
  }
}
