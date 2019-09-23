package controllers

import javax.inject._
import java.io.File
import java.nio.file.Path
import scala.util.control.NonFatal
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }
import play.api.http.ContentTypes
import play.api.libs.json._
import play.api.mvc._
import akka.actor._
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.search.highlight.TokenSources
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.odinson.BuildInfo
import ai.lum.odinson.ExtractorEngine
import ai.lum.odinson.digraph.Vocabulary
import org.apache.lucene.store.FSDirectory
import ai.lum.odinson.lucene.search.OdinsonScoreDoc
import ai.lum.odinson.extra.DocUtils
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.analysis.TokenStreamUtils
import ai.lum.odinson.utils.ConfigFactory
import utils.DocumentMetadata


@Singleton
class OdinsonController @Inject() (system: ActorSystem, cc: ControllerComponents)
  extends AbstractController(cc) {

  val config             = ConfigFactory.load()
  val docIdField         = config[String]("odinson.index.documentIdField")
  val sentenceIdField    = config[String]("odinson.index.sentenceIdField")
  val wordTokenField     = config[String]("odinson.index.wordTokenField")
  val pageSize           = config[Int]("odinson.pageSize")

  val extractorEngine = ExtractorEngine.fromConfig("odinson")
  val odinsonContext: ExecutionContext = system.dispatchers.lookup("contexts.odinson")

  /** convenience methods for formatting Play 2 Json */
  implicit class JsonOps(json: JsValue) {
    def format(pretty: Option[Boolean]): Result = pretty match {
      case Some(true) => Ok(Json.prettyPrint(json)).as(ContentTypes.JSON)
      case _ => Ok(json).as(ContentTypes.JSON)
    }
  }

  def buildInfo(pretty: Option[Boolean]) = Action.async {
    Future {
      val json = jsonBuildInfo
      json.format(pretty)
    }(odinsonContext)
  }

  def numDocs = Action {
    Ok(extractorEngine.indexReader.numDocs.toString).as(ContentTypes.JSON)
  }

  /** Information about the current corpus. <br>
    * Directory name, num docs, num dependency types, etc.
    */
  def corpusInfo(pretty: Option[Boolean]) = Action.async {
    Future {
      val numDocs = extractorEngine.indexReader.numDocs
      val corpusDir = config[File]("odinson.indexDir").getName
      val depsVocabSize = {
        loadVocabulary.terms.toSet.size
      }
      val json = Json.obj(
        "numDocs" -> numDocs,
        "corpus"   -> corpusDir,
        "distinctDependencyRelations" -> depsVocabSize
      )
      json.format(pretty)
    }(odinsonContext)
  }

  def getDocId(luceneDocId: Int): String = {
    val doc = extractorEngine.indexReader.document(luceneDocId)
    doc.getValues(docIdField).head
  }

  def getSentenceIndex(luceneDocId: Int): Int = {
    val doc = extractorEngine.indexReader.document(luceneDocId)
    // FIXME: this isn't safe
    doc.getValues(sentenceIdField).head.toInt
  }

  def loadVocabulary: Vocabulary = {
    val indexPath = config[Path]("odinson.indexDir")
    val indexDir = FSDirectory.open(indexPath)
    Vocabulary.fromDirectory(indexDir)
  }

  /** Retrieves vocabulary of dependencies for the current index.
    */
  def dependenciesVocabulary(pretty: Option[Boolean]) = Action.async {
    Future {
      // NOTE: It's possible the vocabulary could change if the index is updated
      val vocabulary = loadVocabulary
      val vocab = vocabulary.terms.toList.sorted
      val json  = Json.toJson(vocab)
      json.format(pretty)
    }(odinsonContext)
  }

  /** Retrieves JSON for given sentence ID. <br>
    * Used to visualize parse and token attributes.
    */
  def sentenceJsonForSentId(odinsonDocId: Int, pretty: Option[Boolean]) = Action.async {
    Future {
      val json  = mkAbridgedSentence(odinsonDocId)
      json.format(pretty)
    }(odinsonContext)
  }

  // FIXME: add these fields to config under odinson.extra.
  def getParentMetadata(docId: String): DocumentMetadata = {
    val parentDoc = extractorEngine.getParentDoc(docId)
    val authors: Option[Seq[String]] = Try(parentDoc.getFields("author").map(_.stringValue)) match {
      case Success(v) => if (v.nonEmpty) Some(v) else None
      case Failure(_) => None
    }

    val title: Option[String] = Try(parentDoc.getField("title").stringValue) match {
      case Success(v) => if (v.nonEmpty) Some(v) else None
      case Failure(_) => None
    }

    val venue: Option[String] = Try(parentDoc.getField("venue").stringValue) match {
      case Success(v) => if (v.nonEmpty) Some(v) else None
      case Failure(_) => None
    }

    val year: Option[Int] = Try(parentDoc.getField("year").numericValue.intValue) match {
      case Success(v) => Some(v)
      case Failure(_) => None
    }

    val doi: Option[String] = Try(parentDoc.getField("doi").stringValue) match {
      case Success(v) => if (v.nonEmpty) Some(v) else None
      case Failure(_) => None
    }

    val url: Option[String] = Try(parentDoc.getField("url").stringValue) match {
      case Success(v) => if (v.nonEmpty) Some(v) else None
      case Failure(_) => None
    }

    DocumentMetadata(
      docId = docId,
      authors = authors,
      title = title,
      doi = doi,
      url = url,
      year = year,
      venue = venue
    )
  }

  def getParent(docId: String, pretty: Option[Boolean]) = Action.async {
    Future {
      val jdata = getParentMetadata(docId)

      implicit val metadataFormat = Json.format[DocumentMetadata]
      val json = Json.toJson(jdata)
      json.format(pretty)
    }(odinsonContext)
  }

  /** Stores query results in state.
    *
    * @param odinsonQuery An Odinson pattern
    * @param parentQuery A Lucene query to filter documents (optional).
    * @param label The label to use when committing matches.
    */
  def commitResults(
    odinsonQuery: String,
    parentQuery: Option[String],
    label: String = "Mention"
  ): Unit = {
    val results = parentQuery match {
      case None => extractorEngine.query(odinsonQuery)
      case Some(filter) => extractorEngine.query(odinsonQuery, filter)
    }
    for {
      scoreDoc <- results.scoreDocs
      span <- scoreDoc.matches.map(_.span).distinct
    } {
      extractorEngine.state.addMention(
        docBase    = scoreDoc.segmentDocBase,
        docId      = scoreDoc.segmentDocId,
        label      = label,
        startToken = span.start,
        endToken   = span.end
      )
    }
    // index for efficient lookup in subsequent queries
    extractorEngine.state.index()
  }

  /**
    *
    * @param odinsonQuery An Odinson pattern
    * @param parentQuery A Lucene query to filter documents (optional).
    * @param label The label to use when committing matches to the state.
    * @param commit Whether or not results should be committed to the state.
    * @param prevDoc The last Document ID seen on the previous page of results (required if retrieving page 2+).
    * @param prevScore The score of the last Document see on the previous page (required if retrieving page 2+).
    * @return JSON of matches
    */
  def runQuery(
    odinsonQuery: String,
    parentQuery: Option[String],
    label: Option[String], // FIXME: in the future, this will be decided in the grammar
    commit: Option[Boolean], // FIXME: in the future, this will be decided in the grammar
    prevDoc: Option[Int],
    prevScore: Option[Float],
    enriched: Boolean,
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      try {
        val start = System.currentTimeMillis()

        val results: OdinResults = (prevDoc, prevScore) match {
          case (Some(doc), Some(score)) =>
            // continue where we left off
            parentQuery match {
              case None => extractorEngine.query(odinsonQuery, pageSize, doc, score)
              case Some(filter) => extractorEngine.query(odinsonQuery, filter, pageSize, doc, score)
            }
          case _ =>
            // get first page
            parentQuery match {
              case None => extractorEngine.query(odinsonQuery, pageSize)
              case Some(filter) => extractorEngine.query(odinsonQuery, filter, pageSize)
            }
        }
        val duration = (System.currentTimeMillis() - start) / 1000f // duration in seconds

        // should the results be added to the state?
        if (commit.getOrElse(false)) {
          // FIXME: can this be processed in the background?
          commitResults(
            odinsonQuery = odinsonQuery,
            parentQuery = parentQuery,
            label = label.getOrElse("Mention")
          )
        }

        val json = Json.toJson(mkJson(odinsonQuery, parentQuery, duration, results, enriched))
        json.format(pretty)
      } catch {
        case NonFatal(e) =>
          val stackTrace = ExceptionUtils.getStackTrace(e)
          val json = Json.toJson(Json.obj("error" -> stackTrace))
          Status(400)(json)
      }
    }(odinsonContext)
  }

  val jsonBuildInfo: JsValue = Json.obj(
    "name"                  -> BuildInfo.name,
    "version"               -> BuildInfo.version,
    "scalaVersion"          -> BuildInfo.scalaVersion,
    "sbtVersion"            -> BuildInfo.sbtVersion,
    "libraryDependencies"   -> BuildInfo.libraryDependencies,
    "scalacOptions"         -> BuildInfo.scalacOptions,
    "gitCurrentBranch"      -> BuildInfo.gitCurrentBranch,
    "gitHeadCommit"         -> BuildInfo.gitHeadCommit,
    "gitHeadCommitDate"     -> BuildInfo.gitHeadCommitDate,
    "gitUncommittedChanges" -> BuildInfo.gitUncommittedChanges,
    "builtAtString"         -> BuildInfo.builtAtString,
    "builtAtMillis"         -> BuildInfo.builtAtMillis
  )

  def mkJson(odinsonQuery: String, parentQuery: Option[String], duration: Float, results: OdinResults, enriched: Boolean): JsValue = {

    val scoreDocs: JsValue = enriched match {
      case true  => Json.arr(results.scoreDocs.map(mkJsonWithEnrichedResponse):_*)
      case false => Json.arr(results.scoreDocs.map(mkJson):_*)
    }

    Json.obj(
      "odinsonQuery" -> odinsonQuery,
      "parentQuery"  -> parentQuery,
      "duration"     -> duration,
      "totalHits"    -> results.totalHits,
      "scoreDocs"    -> scoreDocs
    )
  }

  def mkJson(odinsonScoreDoc: OdinsonScoreDoc): Json.JsValueWrapper = {
    val doc = extractorEngine.indexSearcher.doc(odinsonScoreDoc.doc)
    val tvs = extractorEngine.indexReader.getTermVectors(odinsonScoreDoc.doc)
    val sentenceText = doc.getField(wordTokenField).stringValue
    val ts = TokenSources.getTokenStream(wordTokenField, tvs, sentenceText, new WhitespaceAnalyzer, -1)
    val tokens = TokenStreamUtils.getTokens(ts)
    Json.obj(
      "odinsonDoc"    -> odinsonScoreDoc.doc,
      "score"         -> odinsonScoreDoc.score,
      "documentId"    -> getDocId(odinsonScoreDoc.doc),
      "sentenceIndex" -> getSentenceIndex(odinsonScoreDoc.doc),
      "words"         -> JsArray(tokens.map(JsString)),
      "matches"       -> Json.arr(odinsonScoreDoc.matches.map(mkJson):_*)
    )
  }

  def mkJson(spanWithCaptures: SpanWithCaptures): Json.JsValueWrapper = {
    Json.obj(
      "span"     -> mkJson(spanWithCaptures.span),
      "captures" -> Json.arr(spanWithCaptures.captures.map(mkJson):_*)
    )
  }

  def mkJson(namedCapture: NamedCapture): Json.JsValueWrapper = {
    Json.obj(namedCapture._1 -> mkJson(namedCapture._2))
  }

  def mkJson(span: Span): Json.JsValueWrapper = {
    Json.obj(
      "start" -> span.start,
      "end"   -> span.end
    )
  }

  def mkJsonWithEnrichedResponse(odinsonScoreDoc: OdinsonScoreDoc): Json.JsValueWrapper = {
    Json.obj(
      "odinsonDoc"    -> odinsonScoreDoc.doc,
      "score"         -> odinsonScoreDoc.score,
      "documentId"    -> getDocId(odinsonScoreDoc.doc),
      "sentenceIndex" -> getSentenceIndex(odinsonScoreDoc.doc),
      "sentence"      -> mkAbridgedSentence(odinsonScoreDoc.doc),
      "matches"       -> Json.arr(odinsonScoreDoc.matches.map(mkJson):_*)
    )
  }

  def retrieveSentenceJson(odinsonDocId: Int): JsValue = {
    val sent  = extractorEngine.indexSearcher.doc(odinsonDocId)
    val bin   = sent.getBinaryValue("json-binary").bytes
    Json.parse(DocUtils.bytesToJsonString(bin))
  }

  def mkAbridgedSentence(odinsonDocId: Int): JsValue = {
    val unabridgedJson = retrieveSentenceJson(odinsonDocId)
    unabridgedJson.as[JsObject] - "startOffsets" - "endOffsets" - "raw"
  }

}
