package controllers

import java.io.{File, IOException}
import java.nio.file.Files

import ai.lum.odinson.extra.IndexDocuments
import ai.lum.odinson.utils.exceptions.OdinsonException
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatestplus.play.guice._
import play.api.test.Helpers._
import org.apache.commons.io.FileUtils
import org.scalatest.TestData
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.libs.functional.syntax._
import org.scalatestplus.play._
import play.api.test._

import scala.reflect.io.Directory

/** Add your spec here.
  * You can mock out a whole application including requests, plugins etc.
  *
  * For more information, see https://www.playframework.com/documentation/latest/ScalaTestingWithScalaTest
  */

class OdinsonControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  case class SingletonRow(term: String, frequency: Double)
  type SingletonRows = Seq[SingletonRow]
  implicit val singletonRowFormat: Format[SingletonRow] = Json.format[SingletonRow]
  implicit val readSingletonRows: Reads[Seq[SingletonRow]] = Reads.seq(singletonRowFormat)

  case class GroupedRow(term: String, group: String, frequency: Double)
  type GroupedRows = Seq[GroupedRow]
  implicit val groupedRowFormat: Format[GroupedRow] = Json.format[GroupedRow]
  implicit val readGroupedRows: Reads[Seq[GroupedRow]] = Reads.seq(groupedRowFormat)

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
      response2Content must not include ("example1")
      response2Content must not include ("GrammaticalSubject")

    }

    "respond with token-based frequencies using the /term-freq endpoint" in {
      val response = route(app, FakeRequest(GET, "/api/term-freq?field=word")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")

      Helpers.contentAsString(response) must include("term")
      Helpers.contentAsString(response) must include("frequency")

      val json = Helpers.contentAsJson(response)

      // check that the response is valid in form
      val rowsResult = json.validate(readSingletonRows)
      rowsResult.isSuccess must be (true)

      // check that default indices are 0 and 9
      val rows: Seq[SingletonRow] = rowsResult match {
        case r: JsResult[SingletonRows] => r.get
        case _ => Nil
      }

      rows must have length (10)

      // check for ordering (greatest to least)
      val freqs = rows.map(_.frequency)
      freqs.zip(freqs.tail).foreach{ case(freq1, freq2) =>
          freq1 must be >= freq2
      }
    }

    "respond with grouped token-based frequencies using the /term-freq endpoint" in {
      val response = route(app, FakeRequest(GET, "/api/term-freq?field=tag&group=raw&min=0&max=4")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")

      Helpers.contentAsString(response) must include("term")
      Helpers.contentAsString(response) must include("group")
      Helpers.contentAsString(response) must include("frequency")

      val json = Helpers.contentAsJson(response)

      // check that the response is of a valid form
      val rowsResult = json.validate(readGroupedRows)
      rowsResult.isSuccess must be (true)

      // check that we have the right number of results
      val rows: Seq[GroupedRow] = rowsResult match {
        case r: JsResult[GroupedRows] => r.get
        case _ => Nil
      }
      // important to save ordering
      val termOrder = rows.map(_.term).distinct.zipWithIndex.toMap
      val rowsForTerm = rows.groupBy(_.term).toSeq.sortBy{ case (term, frequencies) => termOrder(term) }
      rowsForTerm must have length (5)

      // check for ordering among `term`s (greatest to least)
      val termFreqs = rowsForTerm.map{ case (term, rows) => rows.map(_.frequency).sum }
      termFreqs.zip(termFreqs.tail).foreach{ case(freq1, freq2) =>
        freq1 must be >= freq2
      }
    }

    "respond to order and reverse variables in /term-freq endpoint" in {
      val response = route(app, FakeRequest(GET, "/api/term-freq?field=word&order=alpha&reverse=true")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      Helpers.contentAsString(response) must include("term")
      Helpers.contentAsString(response) must include("frequency")

      val json = Helpers.contentAsJson(response)

      // check that the response is valid in form
      val rowsResult = json.validate(readSingletonRows)
      rowsResult.isSuccess must be (true)

      // check that default indices are 0 and 9
      val rows: Seq[SingletonRow] = rowsResult match {
        case r: JsResult[SingletonRows] => r.get
        case _ => Nil
      }

      // check for ordering (reverse Unicode sort) -- no overlap because terms must be distinct
      val terms = rows.map(_.term)
      terms.zip(terms.tail).foreach{ case(term1, term2) =>
        term1 must be > term2
      }
    }

    "filter terms in /term-freq endpoint" in {
      val response = route(app, FakeRequest(GET, "/api/term-freq?field=lemma&filter=the")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      Helpers.contentAsString(response) must include("term")
      Helpers.contentAsString(response) must include("frequency")

      val json = Helpers.contentAsJson(response)

      // check that the response is valid in form
      val rowsResult = json.validate(readSingletonRows)
      rowsResult.isSuccess must be (true)

      val rows: Seq[SingletonRow] = rowsResult match {
        case r: JsResult[SingletonRows] => r.get
        case _ => Nil
      }

      // check that all terms have the right contents
      val terms = rows.map(_.term)
      terms.foreach{ term =>
        term.contains("the")
      }
    }

    val expandedRules =
      s"""
         |rules:
         | - name: "agent-active"
         |   label: Agent
         |   type: event
         |   pattern: |
         |       trigger = [tag=/V.*/]
         |       agent  = >nsubj []
         |
         | - name: "patient-active"
         |   label: Patient
         |   type: event
         |   pattern: |
         |       trigger = [tag=/V.*/]
         |       patient  = >dobj []
         |
         | - name: "agent-passive"
         |   label: Agent
         |   type: event
         |   pattern: |
         |       trigger = [tag=/V.*/]
         |       agent  = >nmod_by []
         |
         | - name: "patient-passive"
         |   label: Patient
         |   type: event
         |   pattern: |
         |       trigger = [tag=/V.*/]
         |       patient  = >nsubjpass []
         |
        """.stripMargin

    "respond with rule-based frequencies using the /rule-freq endpoint" in {
      val body = Json.obj(
        "grammar" -> expandedRules,
        "allowTriggerOverlaps" -> true,
        "order" -> "freq",
        "min" -> 0,
        "max" -> 1,
        "scale" -> "log10",
        "reverse" -> true,
        "pretty" -> true
      )

      val response =
        controller.ruleFreq().apply(FakeRequest(POST, "/api/rule-freq").withJsonBody(body))
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      Helpers.contentAsString(response) must include("term")
      Helpers.contentAsString(response) must include("frequency")
    }

    "respond with frequency table using the simplest possible /term-hist endpoint" in {
      val response = route(app, FakeRequest(GET, "/api/term-hist?field=lemma")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      Helpers.contentAsString(response) must include("w")
      Helpers.contentAsString(response) must include("x")
      Helpers.contentAsString(response) must include("y")
    }

    "respond with frequency table using the maximal /term-hist endpoint" in {
      val response = route(
        app,
        FakeRequest(
          GET,
          "/api/term-hist?field=tag&bins=5&equalProbability=true&xLogScale=true&pretty=true"
        )
      ).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      Helpers.contentAsString(response) must include("w")
      Helpers.contentAsString(response) must include("x")
      Helpers.contentAsString(response) must include("y")
    }

    "respond with frequency table using the simplest possible /rule-hist endpoint" in {
      val body = Json.obj("grammar" -> expandedRules)

      val response =
        controller.ruleHist().apply(FakeRequest(POST, "/api/rule-hist").withJsonBody(body))
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      Helpers.contentAsString(response) must include("w")
      Helpers.contentAsString(response) must include("x")
      Helpers.contentAsString(response) must include("y")
    }

    "respond with frequency table using the maximal /rule-hist endpoint" in {
      val body = Json.obj(
        "grammar" -> expandedRules,
        "allowTriggerOverlaps" -> true,
        "bins" -> 2,
        "equalProbability" -> true,
        "xLogScale" -> true,
        "pretty" -> true
      )

      val response =
        controller.ruleHist().apply(FakeRequest(POST, "/api/rule-hist").withJsonBody(body))
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      Helpers.contentAsString(response) must include("w")
      Helpers.contentAsString(response) must include("x")
      Helpers.contentAsString(response) must include("y")
    }

  }

}
