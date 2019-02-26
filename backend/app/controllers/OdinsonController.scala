package controllers

import java.nio.file.Path

import javax.inject._
import java.io.{ InputStream, File }
import java.nio.charset.StandardCharsets

import scala.util.control.NonFatal
import scala.concurrent.{ ExecutionContext, Future }
import akka.stream.scaladsl.StreamConverters
import play.api.mvc._
import play.api.libs.json._
import akka.actor._
import com.typesafe.config._
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.search.highlight.TokenSources
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.odinson.BuildInfo
import ai.lum.odinson.ExtractorEngine
import ai.lum.odinson.lucene.search.{ OdinsonScoreDoc }
import ai.lum.odinson.extra.DocUtils
import ai.lum.odinson.lucene._
import ai.lum.odinson.highlighter.TokenStreamUtils
import org.apache.commons.io.IOUtils
import utils.{ DocumentMetadata, OdinsonRow }

import scala.util.{ Failure, Success, Try }

@Singleton
class OdinsonController @Inject() (system: ActorSystem, cc: ControllerComponents)
  extends AbstractController(cc) {

  val config             = ConfigFactory.load()
  val indexDir           = config[Path]("odinson.indexDir")

  val docIdField         = config[String]("odinson.index.documentIdField")
  val sentenceIdField    = config[String]("odinson.index.sentenceIdField")
  val wordTokenField     = config[String]("odinson.index.wordTokenField")

  val vocabFile          = config[File]("odinson.compiler.dependenciesVocabulary")

  val extractorEngine = new ExtractorEngine(indexDir)
  val odinsonContext: ExecutionContext = system.dispatchers.lookup("contexts.odinson")

  val pageSize = 15 // TODO move to config?

  def buildInfo(pretty: Option[Boolean]) = Action.async {
    Future {
      val json = jsonBuildInfo
      pretty match {
        case Some(true) => Ok(Json.prettyPrint(json))
        case _ => Ok(json)
      }
    }(odinsonContext)
  }

  def numDocs = Action {
    Ok(extractorEngine.indexReader.numDocs.toString)
  }

  def getDocId(luceneDocId: Int): String = {
    val doc = extractorEngine.indexReader.document(luceneDocId)
    doc.getValues(docIdField).head
  }

  def getSentenceIndex(luceneDocId: Int): Int = {
    val doc = extractorEngine.indexReader.document(luceneDocId)
    doc.getValues(sentenceIdField).head.toInt
  }


  /** Retrieves vocabulary of dependencies for the current index.
    */
  def dependenciesVocabulary(pretty: Option[Boolean]) = Action.async {
    Future {
      val vocab: List[String] = {
        vocabFile
          .readString()
          .lines
          //.flatMap(dep => Seq(s">$dep", s"<$dep"))
          .toList
          .sorted
      }
      val json  = Json.toJson(vocab)
      pretty match {
        case Some(true) => Ok(Json.prettyPrint(json))
        case _ => Ok(json)
      }
    }(odinsonContext)
  }

  /** Retrieves JSON for given sentence ID. <br>
    * Used to visualize parse and token attributes.
    */
  def sentenceJsonForSentId(odinsonDocId: Int, pretty: Option[Boolean]) = Action.async {
    Future {
      val json  = mkAbridgedSentence(odinsonDocId)
      pretty match {
        case Some(true) => Ok(Json.prettyPrint(json))
        case _ => Ok(json)
      }
    }(odinsonContext)
  }

  /**
    * Export query results to a TSV file
    * TSV file structure: <br>
    * QUERY  DOC_ID  SENTENCE_INDEX  TOKENS  START  END  MATCHING_SPAN <br>
    * START and END are character spans relative to the beginning of the sentence
    */
  def exportResults(odinsonQuery: String, parentQuery: Option[String]) = Action.async {
    Future {
      // FIXME: add in parentQuery
      val results = extractorEngine.query(odinsonQuery, extractorEngine.indexReader.maxDoc)

      val rows: Seq[OdinsonRow] = results.scoreDocs.flatMap { d =>

        val doc = extractorEngine.indexSearcher.doc(d.doc)
        val docId = getDocId(d.doc)
        val sentenceIndex = getSentenceIndex(d.doc)
        val sentenceText = doc.getField(wordTokenField).stringValue

        val parentMetadata = getParentMetadata(docId = docId)

        val tokens = {
          val tvs = extractorEngine.indexReader.getTermVectors(d.doc)
          val ts = TokenSources.getTokenStream(wordTokenField, tvs, sentenceText, new WhitespaceAnalyzer, -1)
          TokenStreamUtils.getTokens(ts)
        }


        d.matches.map { m =>
          val matchingSpan = tokens.slice(m.span.start, m.span.end).mkString(" ")
          val row = OdinsonRow(
            odinsonQuery = odinsonQuery,
            parentQuery = parentQuery,
            docId = docId,
            sentenceIndex = sentenceIndex,
            tokens = tokens.toSeq,
            start = m.span.start + tokens.take(m.span.start).map(_.length).sum,
            end = m.span.end - 1 + tokens.take(m.span.end).map(_.length).sum,
            matchingSpan = matchingSpan,
            metadata = parentMetadata
          )
          row
        }
      }

      // FIXME: can this be done as stream (ex. process n rows at a time?)
      val contents = Seq(OdinsonRow.HEADER(delimiter = "\t")) ++ rows.map(_.toRow(delimiter = "\t"))

      // see https://en.wikipedia.org/wiki/Chunked_transfer_encoding
      val stream: InputStream = IOUtils.toInputStream(contents.mkString("\n"), StandardCharsets.UTF_8)
      val src = StreamConverters.fromInputStream(() => stream)
      Ok.chunked(src)
    }(odinsonContext)
  }

  /**
    * Find the most commonly occurring matching spans (top n) for the given query q. <br>
    * Count ignores case.
    */
  def mostCommon(odinsonQuery: String, parentQuery: Option[String], k: Int): Action[AnyContent] = {
    mostCommonForArg(odinsonQuery, parentQuery, k, arg = None, pretty = None)
  }

  /**
    * Find the most commonly occurring matching spans (top n) for the given query q. <br>
    * Count ignores case. Optionally specify a
    */
  def mostCommonForArg(odinsonQuery: String, parentQuery: Option[String], k: Int, arg: Option[String], pretty: Option[Boolean]): Action[AnyContent] = Action.async {
    Future {
      // FIXME: add in parentQuery
      val results = extractorEngine.query(odinsonQuery, extractorEngine.indexReader.maxDoc)

      val matchCounts: Seq[(String, Int)] = results.scoreDocs.par.flatMap { d =>
        val doc = extractorEngine.indexSearcher.doc(d.doc)
        val sentenceText = doc.getField(wordTokenField).stringValue

        val tokens = {
          val tvs = extractorEngine.indexReader.getTermVectors(d.doc)
          val ts = TokenSources.getTokenStream(wordTokenField, tvs, sentenceText, new WhitespaceAnalyzer, -1)
          TokenStreamUtils.getTokens(ts)
        }

        d.matches.flatMap { m =>
          val spans = if (arg.isEmpty) {
            Seq(m.span)
          } else {
            m.captures.filter(_._1 == arg.get).map(_._2)
          }
          spans.map{ span =>
            tokens
              .slice(span.start, span.end)
              .mkString(" ")
              .toLowerCase
          }
        }
      }.groupBy(identity).mapValues(_.length).toSeq.seq

      val res = matchCounts
        .sortBy(_._2)
        .reverse
        .take(k)
        .map(pair => Json.obj("match" -> pair._1, "count" -> pair._2))

      val json = Json.toJson(res)

      pretty match {
        case Some(true) => Ok(Json.prettyPrint(json))
        case _ => Ok(json)
      }
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
      pretty match {
        case Some(true) => Ok(Json.prettyPrint(json))
        case _ => Ok(json)
      }
    }(odinsonContext)
  }

  /**
    *
    * @param odinsonQuery An Odinson pattern
    * @param parentQuery A Lucene query to filter documents (optional).
    * @param prevDoc The last Document ID seen on the previous page of results (required if retrieving page 2+).
    * @param prevScore The score of the last Document see on the previous page (required if retrieving page 2+).
    * @return JSON of matches
    */
  def runQuery(
    odinsonQuery: String,
    parentQuery: Option[String],
    prevDoc: Option[Int],
    prevScore: Option[Float],
    enriched: Boolean,
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      try {
        val start = System.currentTimeMillis()
        val results = (prevDoc, prevScore) match {
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

        val json = Json.toJson(mkJson(odinsonQuery, parentQuery, duration, results, enriched))
        pretty match {
          case Some(true) => Ok(Json.prettyPrint(json))
          case _ => Ok(json)
        }
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
    val doc = extractorEngine.indexSearcher.doc(odinsonScoreDoc.doc)
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
