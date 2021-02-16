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
import play.api.libs.json._

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

  val dataDir  = tmpFolder.getAbsolutePath
  val indexDir = new File(tmpFolder, "index")
  val docsDir  = new File(tmpFolder, "docs").getAbsolutePath

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

  def hasResults(resp: JsValue): Boolean = (resp \ "scoreDocs") match {
    // scoreDocs exists, but what is its type?
    case JsDefined(jsval) => jsval match {
      // if our query matched, we should have a non-empty array here
      case JsArray(array) => array.nonEmpty
      case _ => false
    }
    // scoreDocs not found! :(
    case _ => false
  } 

  def noResults(resp: JsValue): Boolean = hasResults(resp) == false

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

  "OdinsonController" should {

    "access the /buildinfo endpoint from a new instance of controller" in {

      val buildinfo = controller.buildInfo(pretty = None).apply(FakeRequest(GET, "/buildinfo"))

      status(buildinfo) mustBe OK
      contentType(buildinfo) mustBe Some("application/json")
      (contentAsJson(buildinfo) \ "name").as[String] mustBe "odinson-core"

    }

    "process a pattern query using the runQuery method without a parentQuery" in {

      val res = controller.runQuery(
        odinsonQuery = "[lemma=be] []",
        parentQuery = None,
        label = None,
        commit = None,
        prevDoc = None,
        prevScore = None,
        enriched = false,
        pretty = None
      ).apply(FakeRequest(GET, "/pattern"))

      status(res) mustBe OK
      contentType(res) mustBe Some("application/json")
      Helpers.contentAsString(res) must include("core")

    }

    "process a pattern query using the runQuery method with a parentQuery" in {

      val res1 = controller.runQuery(
        odinsonQuery = "[lemma=pie]",
        parentQuery = Some("character:Major.*"),
        label = None,
        commit = None,
        prevDoc = None,
        prevScore = None,
        enriched = false,
        pretty = None
      ).apply(FakeRequest(GET, "/pattern"))

      status(res1) mustBe OK
      contentType(res1) mustBe Some("application/json")
      //println(contentAsJson(res1))
      noResults(contentAsJson(res1)) mustBe true

      val res2 = controller.runQuery(
        odinsonQuery = "[lemma=pie]",
        // See http://www.lucenetutorial.com/lucene-query-syntax.html
        parentQuery = Some("character:Special Agent*"),
        label = None,
        commit = None,
        prevDoc = None,
        prevScore = None,
        enriched = false,
        pretty = None
      ).apply(FakeRequest(GET, "/pattern"))

      status(res2) mustBe OK
      contentType(res2) mustBe Some("application/json")
      println(contentAsJson(res2))
      hasResults(contentAsJson(res2)) mustBe true
    }

    "process a pattern query by accessing the /pattern endpoint" in {
      // the pattern used in this test: "[lemma=be] []"
      val result = route(app, FakeRequest(GET, "/api/execute/pattern?odinsonQuery=%5Blemma%3Dbe%5D%20%5B%5D")).get

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
           |     trigger  = [tag=/VB.*/]
           |     subject  = >nsubj []
           |
        """.stripMargin

      val body = Json.obj(
        // format: off
        "grammar"              -> ruleString,
        "pageSize"             -> 10,
        "allowTriggerOverlaps" -> false
        // format: on
      )

      val response =
        controller.executeGrammar().apply(FakeRequest(POST, "/grammar").withJsonBody(body))
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      Helpers.contentAsString(response) must include("vision")

    }

    "execute a grammar by accessing the /grammar endpoint" in {

      val ruleString =
        s"""
           |rules:
           | - name: "example"
           |   label: GrammaticalSubject
           |   type: event
           |   pattern: |
           |     trigger  = [tag=/VB.*/]
           |     subject  = >nsubj []
           |
        """.stripMargin

      val body = Json.obj(
        // format: off
        "grammar"              -> ruleString,
        "pageSize"             -> 10,
        "allowTriggerOverlaps" -> false
        // format: on
      )

      val response = route(app, FakeRequest(POST, "/api/execute/grammar").withJsonBody(body)).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      Helpers.contentAsString(response) must include("vision")

    }

    "respond with token-based frequencies using the /term-freq endpoint" in {
      val body = Json.obj("field" -> "word")

      val response =
        route(app, FakeRequest(GET, "/api/term-freq?field=word").withJsonBody(body)).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      //println(Helpers.contentAsString(response))
    }

  }

}
