package controllers

import java.io.{File, IOException}
import java.nio.file.Files

import ai.lum.odinson.extra.IndexDocuments
import ai.lum.odinson.utils.exceptions.OdinsonException
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.test.Helpers._
import org.apache.commons.io.FileUtils
import org.scalatest.TestData
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

import scala.reflect.io.Directory

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 *
 * For more information, see https://www.playframework.com/documentation/latest/ScalaTestingWithScalaTest
 */
class OdinsonControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {


  val defaultconfig = ConfigFactory.load()

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val tmpFolder: File = Files.createTempDirectory("odinson-test").toFile()
  val srcDir: File = new File(getClass.getResource("/").getFile)


  try {
    FileUtils.copyDirectory(srcDir, tmpFolder)
  } catch {
    case e: IOException =>
      throw new OdinsonException(s"Can't copy resources directory ${srcDir}")
  }

  val dataDir = tmpFolder.getAbsolutePath
  val indexDir =  new File(tmpFolder, "index")
  val docsDir = new File(tmpFolder, "docs").getAbsolutePath

  val testConfig: Config = {
    defaultconfig
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

  }

  def deleteIndex = {
    val dir = new Directory(indexDir)
    dir.deleteRecursively()
  }

  deleteIndex
  // create index
  IndexDocuments.main(Array(tmpFolder.getAbsolutePath))


  implicit override def newAppForTest(testData: TestData): Application = new GuiceApplicationBuilder()
  .configure("odinson.dataDir" -> ConfigValueFactory.fromAnyRef(dataDir))
  .configure("odinson.indexDir" -> ConfigValueFactory.fromAnyRef(indexDir.getAbsolutePath))
  .configure("odinson.docsDir" -> ConfigValueFactory.fromAnyRef(docsDir))
  .build()


  val controller = new OdinsonController(testConfig, cc = Helpers.stubControllerComponents())

  "OdinsonController GET /buildinfo" should {

    "access the /buildinfo endpoint from a new instance of controller" in {


      val buildinfo = controller.buildInfo(pretty = None).apply(FakeRequest(GET, "/buildinfo"))

      status(buildinfo) mustBe OK
      contentType(buildinfo) mustBe Some("application/json")
      (contentAsJson(buildinfo) \ "name").as[String] mustBe "odinson-core"

    }
  }
}
