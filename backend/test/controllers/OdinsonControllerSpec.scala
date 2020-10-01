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

  //todo: can we use the provider module?
  //todo: dont join paths the way i do
  //todo: tests to annotate test (that's how we can test for java 11 issues)
   val defaultconfig = ConfigFactory.load()

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val tmpFolder: File = Files.createTempDirectory("odinson-test").toFile()

  val srcDir: File = new File(getClass.getResource("/").getFile)



  try {
    FileUtils.copyDirectory(srcDir, tmpFolder)
  } catch {
    case e: IOException =>
      throw new OdinsonException("Can't copy resources directory")
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
  // run stuff
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


  "HomeController GET /pattern" should {

    "access the /pattern endpoint from a new instance of controller" in {

      val pattern = controller.runQuery("[lemma=be] []", None, None, None, None, None, false, None).apply(FakeRequest(GET, "/pattern"))

      status(pattern) mustBe OK
      contentType(pattern) mustBe Some("application/json")
      Helpers.contentAsString(pattern) must include ("core")



    }

//    "render the index page from a new instance of controller" in {
//      val controller = new HomeController(stubControllerComponents())
//      val home = controller.index().apply(FakeRequest(GET, "/"))
//
//      status(home) mustBe OK
//      contentType(home) mustBe Some("text/html")
//      contentAsString(home) must include ("Welcome to Play")
//    }
//
//    "render the index page from the application" in {
//      val controller = inject[HomeController]
//      val home = controller.index().apply(FakeRequest(GET, "/"))
//
//      status(home) mustBe OK
//      contentType(home) mustBe Some("text/html")
//      contentAsString(home) must include ("Welcome to Play")
//    }
//
//    "render the index page from the router" in {
//      val request = FakeRequest(GET, "/")
//      val home = route(app, request).get
//
//      status(home) mustBe OK
//      contentType(home) mustBe Some("text/html")
//      contentAsString(home) must include ("Welcome to Play")
//    }
  }
}
