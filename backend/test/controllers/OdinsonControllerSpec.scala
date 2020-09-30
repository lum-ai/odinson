package controllers

import java.io.{File, IOException}

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.test.Helpers._
import org.apache.commons.io.FileUtils
import org.scalatest.TestData
import play.api.{Application}
import play.api.inject.guice.GuiceApplicationBuilder

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 *
 * For more information, see https://www.playframework.com/documentation/latest/ScalaTestingWithScalaTest
 */
class OdinsonControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {


   val defaultconfig = ConfigFactory.load()

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val tmpFolder = "/tmp/"


  val srcDir = new File("/home/alexeeva/Repos/odinson/extra/src/test/resources")
  val destDir = new File(tmpFolder)
  //
//  try {
//    FileUtils.copyDirectory(srcDir, destDir);
//  } catch {
//    case e: IOException =>
//      println("Can't copy resources directory")
//  }

  val testConfig: Config = {
    defaultconfig
      .withValue("odinson.dataDir", ConfigValueFactory.fromAnyRef(tmpFolder))
      // re-compute the index and docs path's
      .withValue(
      "odinson.indexDir",
      ConfigValueFactory.fromAnyRef(tmpFolder + "index")
    )
      .withValue(
        "odinson.docsDir",
        ConfigValueFactory.fromAnyRef(tmpFolder + "docs")
      )

  }


  //todo: either copy resources needed to the temp dir or point to them; later make this whole thing a util


  implicit override def newAppForTest(testData: TestData): Application = new GuiceApplicationBuilder()
  .configure("odinson.dataDir" -> ConfigValueFactory.fromAnyRef(tmpFolder))
  .configure("odinson.indexDir" -> ConfigValueFactory.fromAnyRef(tmpFolder + "index"))
  .configure("odinson.docsDir" -> ConfigValueFactory.fromAnyRef(tmpFolder + "docs"))
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

      val pattern = controller.runQuery("[lemma=have] []", None, None, None, None, None, false, None).apply(FakeRequest(GET, "/pattern"))

      status(pattern) mustBe OK
      contentType(pattern) mustBe Some("application/json")
      Helpers.contentAsString(pattern) must include ("Greenly")
      Helpers.contentAsString(pattern) must include ("Petitioner")


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
