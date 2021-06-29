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
import play.api.cache.AsyncCacheApi
import play.api.test._

import scala.reflect.io.Directory

class OdinsonControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {
  // for testing `term-freq` endpoint
  case class SingletonRow(term: String, frequency: Double)
  type SingletonRows = Seq[SingletonRow]
  implicit val singletonRowFormat: Format[SingletonRow] = Json.format[SingletonRow]
  implicit val readSingletonRows: Reads[Seq[SingletonRow]] = Reads.seq(singletonRowFormat)

  // for testing `term-freq` endpoint
  case class GroupedRow(term: String, group: String, frequency: Double)
  type GroupedRows = Seq[GroupedRow]
  implicit val groupedRowFormat: Format[GroupedRow] = Json.format[GroupedRow]
  implicit val readGroupedRows: Reads[Seq[GroupedRow]] = Reads.seq(groupedRowFormat)

  val defaultConfig: Config = ConfigFactory.load()

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val tmpFolder: File = Files.createTempDirectory("odinson-test").toFile
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
    defaultConfig
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
        case _              => false
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

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure(
      Map(
        "odinson.dataDir" -> ConfigValueFactory.fromAnyRef(dataDir),
        "odinson.indexDir" -> ConfigValueFactory.fromAnyRef(indexDir.getAbsolutePath),
        "odinson.docsDir" -> ConfigValueFactory.fromAnyRef(docsDir)
      )
    )
    .build()

  implicit override def newAppForTest(testData: TestData): Application = fakeApplication()

  val fakeApp: Application = fakeApplication()

  val controller =
    new OdinsonController(
      testConfig,
      fakeApp.configuration,
      fakeApp.injector.instanceOf[AsyncCacheApi],
      cc = Helpers.stubControllerComponents()
    )

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
        parentQuery = Some("character:Maj*"),
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
      hasResults(contentAsJson(res2)) mustBe true
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

    "retrieve metadata using the /metadata/by-sentence-id endpoint" in {
      val response = route(app, FakeRequest(GET, "/api/metadata/by-sentence-id?sentenceId=2")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")

      Helpers.contentAsString(response) must include("ai.lum.odinson.TokensField")
      Helpers.contentAsString(response) must include("Garland")
    }

    "retrieve metadata using the /metadata/by-document-id endpoint" in {
      val response =
        route(app, FakeRequest(GET, "/api/metadata/by-document-id?documentId=tp-pies")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")

      Helpers.contentAsString(response) must include("ai.lum.odinson.TokensField")
      Helpers.contentAsString(response) must include("Cooper")
    }

    "retrieve the parent doc using the /parent/by-sentence-id endpoint" in {
      val response = route(app, FakeRequest(GET, "/api/parent/by-sentence-id?sentenceId=2")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")

      Helpers.contentAsString(response) must include("Briggs") // metadata
      Helpers.contentAsString(response) must include("subconscious") // this sentence
      Helpers.contentAsString(response) must include("veranda") // other sentences in parent
    }

    "retrieve the parent doc using the /parent/by-document-id endpoint" in {
      val response =
        route(app, FakeRequest(GET, "/api/parent/by-document-id?documentId=tp-pies")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")

      Helpers.contentAsString(response) must include("MacLachlan") // metadata
      Helpers.contentAsString(response) must include("pies") // sentence info
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
      rowsResult.isSuccess must be(true)

      // check that 10 results are returned by default
      val rows: Seq[SingletonRow] = rowsResult match {
        case r: JsResult[SingletonRows] => r.get
        case _                          => Nil
      }

      rows must have length (10)

      // check for ordering (greatest to least)
      val freqs = rows.map(_.frequency)
      freqs.zip(freqs.tail).foreach { case (freq1, freq2) =>
        freq1 must be >= freq2
      }
    }

    "respond with grouped token-based frequencies using the /term-freq endpoint" in {
      val response =
        route(app, FakeRequest(GET, "/api/term-freq?field=tag&group=raw&min=0&max=4")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")

      Helpers.contentAsString(response) must include("term")
      Helpers.contentAsString(response) must include("group")
      Helpers.contentAsString(response) must include("frequency")

      val json = Helpers.contentAsJson(response)

      // check that the response is of a valid form
      val rowsResult = json.validate(readGroupedRows)
      rowsResult.isSuccess must be(true)

      // check that we have the right number of results
      val rows: Seq[GroupedRow] = rowsResult match {
        case r: JsResult[GroupedRows] => r.get
        case _                        => Nil
      }
      // important to save ordering
      val termOrder = rows.map(_.term).distinct.zipWithIndex.toMap
      val rowsForTerm = rows.groupBy(_.term).toSeq.sortBy { case (term, frequencies) =>
        termOrder(term)
      }
      rowsForTerm must have length (5)

      // check for ordering among `term`s (greatest to least)
      val termFreqs = rowsForTerm.map { case (term, rows) => rows.map(_.frequency).sum }
      termFreqs.zip(termFreqs.tail).foreach { case (freq1, freq2) =>
        freq1 must be >= freq2
      }
    }

    "respond to order and reverse variables in /term-freq endpoint" in {
      val response =
        route(app, FakeRequest(GET, "/api/term-freq?field=word&order=alpha&reverse=true")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      Helpers.contentAsString(response) must include("term")
      Helpers.contentAsString(response) must include("frequency")

      val json = Helpers.contentAsJson(response)

      // check that the response is valid in form
      val rowsResult = json.validate(readSingletonRows)
      rowsResult.isSuccess must be(true)

      // check that 10 results are returned by default
      val rows: Seq[SingletonRow] = rowsResult match {
        case r: JsResult[SingletonRows] => r.get
        case _                          => Nil
      }

      // check for ordering (reverse Unicode sort)
      val terms = rows.map(_.term)
      terms.zip(terms.tail).foreach { case (term1, term2) =>
        // no overlap because terms must be distinct
        term1 must be > term2
      }
    }

    "filter terms in /term-freq endpoint" in {
      // filter: `th.*`
      val response = route(app, FakeRequest(GET, "/api/term-freq?field=lemma&filter=th.*")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      Helpers.contentAsString(response) must include("term")
      Helpers.contentAsString(response) must include("frequency")

      val json = Helpers.contentAsJson(response)

      // check that the response is valid in form
      val rowsResult = json.validate(readSingletonRows)
      rowsResult.isSuccess must be(true)

      val rows: Seq[SingletonRow] = rowsResult match {
        case r: JsResult[SingletonRows] => r.get
        case _                          => Nil
      }

      // regex is `^t` and is meant to be unanchored (on the right) and case sensitive
      // check that all terms begin with lowercase t, and that there's at least one term that isn't
      // just `t`
      val terms = rows.map(_.term)
      terms.foreach { term =>
        term.startsWith("th") mustBe (true)
      }
      terms.exists { term =>
        term.length > 1
      } mustBe (true)
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
      //println(Helpers.contentAsString(response))
      // println(Helpers.contentAsString(response))
      Helpers.contentAsString(response) must include("w")
      Helpers.contentAsString(response) must include("x")
      Helpers.contentAsString(response) must include("y")
    }

    "respond with corpus information using the /corpus endpoint" in {
      val response = route(app, FakeRequest(GET, "/api/corpus")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      val responseString = Helpers.contentAsString(response)
      responseString must include("numDocs")
      responseString must include("corpus")
      responseString must include("distinctDependencyRelations")
      responseString must include("tokenFields")
      responseString must include("docFields")
      responseString must include("storedFields")
    }

    "respond with dependencies list using the /dependencies-vocabulary endpoint" in {
      val response = route(app, FakeRequest(GET, "/api/dependencies-vocabulary")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      val json = Helpers.contentAsJson(response)
      val deps = json.as[Array[String]]
      deps must contain("nsubj")
      deps must contain("nmod_from")
    }

    "respond with dependencies list using the /tags-vocabulary endpoint" in {
      val response = route(app, FakeRequest(GET, "/api/tags-vocabulary")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      val json = Helpers.contentAsJson(response)
      val tags = json.as[Array[String]]
      tags must contain("VBG")
      tags must contain("WRB")
    }

  }

}
