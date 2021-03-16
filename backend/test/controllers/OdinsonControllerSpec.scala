package controllers

import java.io.{ File, IOException }
import java.nio.file.Files

import ai.lum.odinson.extra.IndexDocuments
import ai.lum.odinson.utils.exceptions.OdinsonException
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import org.scalatestplus.play.guice._
import play.api.test.Helpers._
import org.apache.commons.io.FileUtils
import org.scalatest.TestData
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json

import org.scalatestplus.play._
import play.api.test._

import scala.reflect.io.Directory

/** Add your spec here.
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
  val indexDir = new File(tmpFolder, "index")
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

  implicit override def newAppForTest(testData: TestData): Application =
    new GuiceApplicationBuilder()
      .configure("odinson.dataDir" -> ConfigValueFactory.fromAnyRef(dataDir))
      .configure("odinson.indexDir" -> ConfigValueFactory.fromAnyRef(indexDir.getAbsolutePath))
      .configure("odinson.docsDir" -> ConfigValueFactory.fromAnyRef(docsDir))
      .build()

  val controller = new OdinsonController(testConfig, cc = Helpers.stubControllerComponents())

  "OdinsonController" should {

    "access the /buildinfo endpoint from a new instance of controller" in {

      val buildinfo = controller.buildInfo(pretty = None).apply(FakeRequest(GET, "/buildinfo"))

      status(buildinfo) mustBe OK
      contentType(buildinfo) mustBe Some("application/json")
      (contentAsJson(buildinfo) \ "name").as[String] mustBe "odinson-core"

    }

    "process a pattern query using the runQuery method" in {

      val pattern = controller.runQuery(
        odinsonQuery = "[lemma=be] []",
        parentQuery = None,
        label = None,
        commit = None,
        prevDoc = None,
        prevScore = None,
        enriched = false,
        pretty = None
      ).apply(FakeRequest(GET, "/pattern"))

      status(pattern) mustBe OK
      contentType(pattern) mustBe Some("application/json")
      Helpers.contentAsString(pattern) must include("core")

    }

    "process a pattern query by accessing the /pattern endpoint" in {
      // the pattern used in this test: "[lemma=be] []"
      val result = route(
        app,
        FakeRequest(GET, "/api/execute/pattern?odinsonQuery=%5Blemma%3Dbe%5D%20%5B%5D")
      ).get

      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      Helpers.contentAsString(result) must include("core")

    }

    "execute a grammar using the executeGrammar method" in {

      val ruleString =
        s"""
           |rules:
           | - name: "example"
           |   label: GrammaticalSubject
           |   type: event
           |   pattern: |
           |       trigger = [lemma=have]
           |       subject  = >nsubj []
           |
        """.stripMargin

      val body =
        Json.obj("grammar" -> ruleString, "pageSize" -> 10, "allowTriggerOverlaps" -> false)

      val response =
        controller.executeGrammar().apply(FakeRequest(POST, "/grammar").withJsonBody(body))
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      Helpers.contentAsString(response) must include("adults")

    }

    "execute a grammar by accessing the /grammar endpoint" in {

      val ruleString =
        s"""
           |rules:
           | - name: "example"
           |   label: GrammaticalSubject
           |   type: event
           |   pattern: |
           |       trigger = [lemma=have]
           |       subject  = >nsubj []
           |
        """.stripMargin

      val body =
        Json.obj("grammar" -> ruleString, "pageSize" -> 10, "allowTriggerOverlaps" -> false)

      val response = route(app, FakeRequest(POST, "/api/execute/grammar").withJsonBody(body)).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      Helpers.contentAsString(response) must include("adults")

    }

    "not persist state across uses of the /grammar endpoint" in {

      val ruleString1 =
        s"""
           |rules:
           | - name: "example1"
           |   label: GrammaticalSubject
           |   type: event
           |   pattern: |
           |       trigger = [lemma=have]
           |       subject  = >nsubj []
           |
        """.stripMargin

      val body1 =
        Json.obj("grammar" -> ruleString1, "pageSize" -> 10, "allowTriggerOverlaps" -> false)

      val response1 = route(app, FakeRequest(POST, "/api/execute/grammar").withJsonBody(body1)).get

      status(response1) mustBe OK
      contentType(response1) mustBe Some("application/json")
      Helpers.contentAsString(response1) must include("example1")

      val ruleString2 =
        s"""
           |rules:
           | - name: "example2"
           |   label: GrammaticalObject
           |   type: event
           |   pattern: |
           |       trigger = [lemma=have]
           |       subject  = >dobj []
           |
        """.stripMargin

      val body2 =
        Json.obj("grammar" -> ruleString2, "pageSize" -> 10, "allowTriggerOverlaps" -> false)

      val response2 = route(app, FakeRequest(POST, "/api/execute/grammar").withJsonBody(body2)).get

      status(response2) mustBe OK
      contentType(response2) mustBe Some("application/json")
      val response2Content = Helpers.contentAsString(response2)
      response2Content must include("example2")
      response2Content must include("GrammaticalObject")
      response2Content must not include("example1")
      response2Content must not include("GrammaticalSubject")

    }

    "respond with token-based frequencies using the /term-freq endpoint" in {
      val body = Json.obj("field" -> "word")

      val response =
        route(app, FakeRequest(GET, "/api/term-freq?field=word").withJsonBody(body)).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      println(Helpers.contentAsString(response))
    }

  }

}
