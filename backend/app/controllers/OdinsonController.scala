package controllers

import java.io.File
import java.nio.file.Path
import javax.inject._
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
import com.typesafe.config.ConfigRenderOptions
import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.odinson.{ BuildInfo, ExtractorEngine, OdinsonMatch, NamedCapture }
import ai.lum.odinson.digraph.Vocabulary
import org.apache.lucene.store.FSDirectory
import ai.lum.odinson.extra.DocUtils
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.analysis.TokenStreamUtils
import ai.lum.odinson.lucene.search.{ OdinsonScoreDoc, OdinsonQuery }
import ai.lum.odinson.Mention

@Singleton
class OdinsonController @Inject() (system: ActorSystem, cc: ControllerComponents)
  extends AbstractController(cc) {

  val config             = ConfigFactory.load()
  val docsDir            = config[File]("odinson.docsDir")
  val docIdField         = config[String]("odinson.index.documentIdField")
  val sentenceIdField    = config[String]("odinson.index.sentenceIdField")
  val wordTokenField     = config[String]("odinson.displayField")
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

  def buildInfo(pretty: Option[Boolean]) = Action {
    Ok(BuildInfo.toJson).as(ContentTypes.JSON)
  }

  def configInfo(pretty: Option[Boolean]) = Action {
    val options = ConfigRenderOptions.concise.setJson(true)
    val json = Json.parse(config.root.render(options))
    json.format(pretty)
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
      // ensure doc id is correct
      val json  = mkAbridgedSentence(odinsonDocId)
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
      odinsonMatch <- scoreDoc.matches
    } {
      extractorEngine.state.addMention(
        docBase    = scoreDoc.segmentDocBase,
        docId      = scoreDoc.segmentDocId,
        label      = label,
        startToken = odinsonMatch.start,
        endToken   = odinsonMatch.end
      )
    }
    // index for efficient lookup in subsequent queries
    extractorEngine.state.index()
  }

  /**
    * Queries the index.
    * 
    * @param odisonQuery An OdinsonQuery
    * @param prevDoc The last Document ID seen on the previous page of results (required if retrieving page 2+).
    * @param prevScore The score of the last Document see on the previous page (required if retrieving page 2+).
    * @return JSON of matches
  */
  def retrieveResults(
    odinsonQuery: OdinsonQuery,
    prevDoc: Option[Int],
    prevScore: Option[Float],
  ): OdinResults = {
    (prevDoc, prevScore) match {
      case (Some(doc), Some(score)) =>
        val osd = new OdinsonScoreDoc(doc, score)
        extractorEngine.query(odinsonQuery, pageSize, osd)
      case _ =>
        extractorEngine.query(odinsonQuery, pageSize)
    }
  }

  case class GrammarRequest(
    rules: String,
    parentQuery: Option[String] = None,
    pageSize: Option[Int] = None,
    allowTriggerOverlaps: Option[Boolean] = None,
    pretty: Option[Boolean] = None
  )

  object GrammarRequest {
    implicit val fmt = Json.format[GrammarRequest]
    implicit val read = Json.reads[GrammarRequest]
  }

  // import play.api.libs.json.Json

  /**
  * Executes the provided Odinson grammar.
  * 
  * @param rules An Odinson grammar
  * @param parentQuery A Lucene query to filter documents (optional).
  * @param pageSize The maximum number of sentences to execute the rules against.
  * @param allowTriggerOverlaps Whether or not event arguments are permitted to overlap with the event's trigger. 
  * @return JSON of matches
  */
  def executeGrammar() = Action { request => 
    println(s"body: ${request.body}")
    //val json: JsValue = request.body.asJson.get
    // FIXME: replace .get with validation check
    val gr = request.body.asJson.get.as[GrammarRequest]
    println(s"GrammarRequest: ${gr}")
    val rules = gr.rules
    val parentQuery = gr.parentQuery
    val pageSize = gr.pageSize
    val allowTriggerOverlaps = gr.allowTriggerOverlaps.getOrElse(false)
    val pretty = gr.pretty
    try {
      // rules -> OdinsonQuery
      val baseExtractors = extractorEngine.ruleReader.compileRuleFile(rules)
      val composedExtractors = parentQuery match {
        case Some(pq) => 
          val cpq = extractorEngine.compiler.mkParentQuery(pq)
          baseExtractors.map(be => be.copy(query = extractorEngine.compiler.mkQuery(be.query, cpq)))
        case None => baseExtractors
      }
      val start = System.currentTimeMillis()

      val maxSentences: Int = pageSize match {
        case Some(ps) => ps
        case None => extractorEngine.numDocs()
      }

      val mentions: Seq[Mention] = extractorEngine.extractMentions(composedExtractors, numSentences = maxSentences, allowTriggerOverlaps = allowTriggerOverlaps)
      
      val duration = (System.currentTimeMillis() - start) / 1000f // duration in seconds

      val json = Json.toJson(mkJson(parentQuery, duration, allowTriggerOverlaps, mentions))
      json.format(pretty)
    } catch {
      case NonFatal(e) =>
        val stackTrace = ExceptionUtils.getStackTrace(e)
        val json = Json.toJson(Json.obj("error" -> stackTrace))
        Status(400)(json)
    }
  }//(odinsonContext)

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
       val oq = parentQuery match {
          case Some(pq) => 
            extractorEngine.compiler.mkQuery(odinsonQuery, pq)
          case None =>
            extractorEngine.compiler.mkQuery(odinsonQuery)
       }
        val start = System.currentTimeMillis()
        val results: OdinResults = retrieveResults(oq, prevDoc, prevScore)
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

  def mkJson(
    odinsonQuery: String, 
    parentQuery: Option[String], 
    duration: Float, 
    results: OdinResults, 
    enriched: Boolean
  ): JsValue = {

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

  /** Process results from executeGrammar */
  def mkJson(
    parentQuery: Option[String], 
    duration: Float, 
    allowTriggerOverlaps: Boolean, 
    mentions: Seq[Mention]
  ): JsValue = {

    val mentionsJson: JsValue = Json.arr(mentions.map(mkJson):_*)

    Json.obj(
      "parentQuery"  -> parentQuery,
      "duration"     -> duration,
      "allowTriggerOverlaps" -> allowTriggerOverlaps,
      "mentions"    -> mentionsJson
    )
  }

  def mkJson(mention: Mention): Json.JsValueWrapper = {
    val doc = extractorEngine.indexSearcher.doc(mention.luceneDocId)
    val tvs = extractorEngine.indexReader.getTermVectors(mention.luceneDocId)
    val sentenceText = doc.getField(wordTokenField).stringValue
    val ts = TokenSources.getTokenStream(wordTokenField, tvs, sentenceText, new WhitespaceAnalyzer, -1)
    val tokens = TokenStreamUtils.getTokens(ts)

      // odinsonMatch: OdinsonMatch,
    Json.obj(
      "odinsonDoc"    -> mention.luceneDocId,
      // "score"         -> odinsonScoreDoc.score,
      "label"         -> mention.label,
      "documentId"    -> getDocId(mention.luceneDocId),
      "sentenceIndex" -> getSentenceIndex(mention.luceneDocId),
      "words"         -> JsArray(tokens.map(JsString)),
      "foundBy"       -> mention.foundBy,
      "match"       -> Json.arr(mkJson(mention.odinsonMatch))
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

  def mkJson(m: OdinsonMatch): Json.JsValueWrapper = {
    Json.obj(
      "span"     -> Json.obj("start" -> m.start, "end" -> m.end),
      "captures" -> Json.arr(m.namedCaptures.map(mkJson):_*)
    )
  }

  def mkJson(namedCapture: NamedCapture): Json.JsValueWrapper = {
    Json.obj(namedCapture.name -> mkJson(namedCapture.capturedMatch))
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

  def retrieveSentenceJson(sentenceIndex: Int, parentDocId: String): JsValue = {
    val parentDoc = extractorEngine.getParentDoc(parentDocId)
    val odinsonDocFile = new File(docsDir, parentDoc.getField("fileName").stringValue)
    val docJson: JsValue = Json.parse(odinsonDocFile.readString(java.nio.charset.StandardCharsets.UTF_8))
    (docJson \ "sentences")(sentenceIndex)
  }

  def mkAbridgedSentence(odinsonDocId: Int): JsValue = {
    val sentenceIndex = getSentenceIndex(odinsonDocId)
    val parentId = getDocId(odinsonDocId)
    val unabridgedJson = retrieveSentenceJson(sentenceIndex, parentId)
    unabridgedJson
    //unabridgedJson.as[JsObject] - "startOffsets" - "endOffsets" - "raw"
  }

}
