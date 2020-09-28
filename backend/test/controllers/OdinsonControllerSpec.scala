package controllers

import java.io.{File, IOException}


import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.test.Helpers._


import org.apache.commons.io.FileUtils

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 *
 * For more information, see https://www.playframework.com/documentation/latest/ScalaTestingWithScalaTest
 */
class OdinsonControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

   val defaultconfig = ConfigFactory.load() //my note--copy elsewhere: finds for application.conf and if the stuff we need is there then searches for the other ones

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val tmpFolder = "/tmp/"


  val srcDir = new File("/home/alexeeva/Repos/odinson/extra/src/test/resources")
  val destDir = new File(tmpFolder)
  //
  try {
    FileUtils.copyDirectory(srcDir, destDir);
  } catch {
    case e: IOException =>
      println("Can't copy resources directory")
  }

  val testConfig: Config = {
    defaultconfig
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
  }

  //todo: either copy resources needed to the temp dir or point to them; later make this whole thing a util

//  def deleteIndex = {
//    val dir = new Directory(new File(tmpFolder + "index"))
//    println("Deleting dir in spec: " + dir)
//    dir.deleteRecursively()
//  }

  val controller = new OdinsonController(testConfig, cc = Helpers.stubControllerComponents())

//  println(testConfig)
//  println(controller.extractorEngine +  "<-")
//  println("got controller HERE: " + controller.docsDir + " " + controller.indexDir )

  "OdinsonController GET /buildinfo" should {

    "access the /buildinfo endpoint from a new instance of controller" in {

      println("got controller HERE: " + controller.docsDir + " " + controller.indexDir )

      val buildinfo = controller.buildInfo(pretty = None).apply(FakeRequest(GET, "/buildinfo"))

      status(buildinfo) mustBe OK
      contentType(buildinfo) mustBe Some("application/json")
      (contentAsJson(buildinfo) \ "name").as[String] mustBe "odinson-rest-api"

    }
//  "HomeController GET /pattern" should {
//
//    "access the /pattern endpoint from a new instance of controller" in {
//
//      val pattern = controller.runQuery("[lemma=have] []", None, None, None, None, None, false, None).apply(FakeRequest(GET, "/pattern"))
//
//      status(pattern) mustBe OK
//      contentType(pattern) mustBe Some("application/json")
//      (contentAsJson(pattern) \ "name").as[String] mustBe "odinson-rest-api"
//
//    }

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
