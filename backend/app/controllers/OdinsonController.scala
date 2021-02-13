package controllers

import java.io.File
import java.nio.file.Path

import javax.inject._

import scala.collection.JavaConverters._
import scala.collection.mutable.Map
import scala.util.control.NonFatal
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }
import play.api.http.ContentTypes
import play.api.libs.json._
import play.api.mvc._
import akka.actor._
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.document.{ Document => LuceneDocument }
import org.apache.lucene.search.highlight.TokenSources
import org.apache.lucene.index.MultiFields
import com.typesafe.config.ConfigRenderOptions
import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.odinson.{
  BuildInfo,
  ExtractorEngine,
  Mention,
  NamedCapture,
  OdinsonMatch,
  Document => OdinsonDocument
}
import ai.lum.odinson.digraph.Vocabulary
import org.apache.lucene.store.FSDirectory
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.analysis.TokenStreamUtils
import ai.lum.odinson.lucene.search.{ OdinsonQuery, OdinsonScoreDoc }
import com.typesafe.config.Config

@Singleton
class OdinsonController @Inject() (
  config: Config = ConfigFactory.load(),
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {
  // before testing, we would create configs to pass to the constructor? write test for build like ghp's example

  val extractorEngine: ExtractorEngine = ExtractorEngine.fromConfig(config)
  // format: off
  val docsDir           = config[File]  ("odinson.docsDir")
  val DOC_ID_FIELD      = config[String]("odinson.index.documentIdField")
  val SENTENCE_ID_FIELD = config[String]("odinson.index.sentenceIdField")
  val WORD_TOKEN_FIELD  = config[String]("odinson.displayField")
  val pageSize          = config[Int]   ("odinson.pageSize")
  // format: on

  //  val extractorEngine = opm.extractorEngineProvider()
  /** convenience methods for formatting Play 2 Json */
  implicit class JsonOps(json: JsValue) {

    def format(pretty: Option[Boolean]): Result = pretty match {
      case Some(true) => Ok(Json.prettyPrint(json)).as(ContentTypes.JSON)
      case _          => Ok(json).as(ContentTypes.JSON)
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

  /** For a given term field, find the terms ranked min to max (inclusive, 0-indexed)
    *
    * @param field raw, token, lemma, tag, etc.
    * @param order "freq" for greatest-to least frequency (default), "alpha" for alphanumeric order
    */
  def termFreq(
    field: String,
    order: Option[String],
    min: Option[Int],
    max: Option[Int],
    scale: Option[String],
    reverse: Option[Boolean],
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      // ensure that the requested field exists in the index
      val fields = MultiFields.getFields(extractorEngine.indexReader)
      val fieldNames = fields.iterator.asScala.toList
      // if the field exists, find the frequencies of each term
      if (fieldNames contains field) {
        // find the frequency of all terms in this field
        val terms = fields.terms(field)
        val termsEnum = terms.iterator()
        val termFreqs = scala.collection.mutable.HashMap[String, Long]()
        while (termsEnum.next() != null) {
          val term = termsEnum.term.utf8ToString
          termFreqs.update(term, termsEnum.totalTermFreq)
        }

        // order the resulting frequencies as requested
        val ordered = order match {
          // alphabetical
          case Some("alpha") => termFreqs.toSeq.sortBy { case (term, _) =>
              term
            }
          // alphabetical
          case _ => termFreqs.toSeq.sortBy { case (term, freq) =>
              (-freq, term)
            }
        }

        // reverse if necessary
        val reversed = reverse match {
          case Some(true) => ordered.reverse
          case _          => ordered
        }

        // transform the frequencies as requested
        val scaled: Seq[(String, Double)] = scale match {
          case Some("log10") => reversed map { case (term, freq) =>
              (term, math.log10(freq))
            }
          case Some("percent") =>
            val countTotal =
              reversed.foldLeft(0.toLong)((s, term) => s + term._2)
            reversed map { case (term, freq) =>
              (term, freq.toDouble / countTotal)
            }
          case _ => reversed.map { case (term, freq) => (term, freq.toDouble) }
        }

        // cutoff the results to the requested ranks
        val defaultMin = 0
        val defaultMax = 9
        val res =
          scaled.slice(min.getOrElse(defaultMin), max.getOrElse(defaultMax) + 1)

        Json.toJson(res.toMap).format(pretty)
      } else {
        // the requested field isn't in this index
        Json.obj().format(pretty)
      }
    }
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
      val fields = MultiFields.getFields(extractorEngine.indexReader)
      val fieldNames = fields.iterator.asScala.toList
      val json = Json.obj(
        "numDocs" -> numDocs,
        "corpus" -> corpusDir,
        "distinctDependencyRelations" -> depsVocabSize,
        "fields" -> fieldNames
      )
      json.format(pretty)
    }
  }

  def getDocId(luceneDocId: Int): String = {
    val doc: LuceneDocument = extractorEngine.indexReader.document(luceneDocId)
    doc.getValues(DOC_ID_FIELD).head
  }

  def getSentenceIndex(luceneDocId: Int): Int = {
    val doc = extractorEngine.indexReader.document(luceneDocId)
    // FIXME: this isn't safe
    doc.getValues(SENTENCE_ID_FIELD).head.toInt
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
      val json = Json.toJson(vocab)
      json.format(pretty)
    }
  }

  /** Retrieves JSON for given sentence ID. <br>
    * Used to visualize parse and token attributes.
    */
  def sentenceJsonForSentId(sentenceId: Int, pretty: Option[Boolean]) =
    Action.async {
      Future {
        // ensure doc id is correct
        val json = mkAbridgedSentence(sentenceId)
        json.format(pretty)
      }
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
      case None =>
        val q = extractorEngine.compiler.mkQuery(odinsonQuery)
        extractorEngine.query(q)
      case Some(filter) =>
        val q = extractorEngine.compiler.mkQuery(odinsonQuery, filter)
        extractorEngine.query(q)
    }
  }

  /** Queries the index.
    *
    * @param odisonQuery An OdinsonQuery
    * @param prevDoc The last Document ID seen on the previous page of results (required if retrieving page 2+).
    * @param prevScore The score of the last Document see on the previous page (required if retrieving page 2+).
    * @return JSON of matches
    */
  def retrieveResults(
    odinsonQuery: OdinsonQuery,
    prevDoc: Option[Int],
    prevScore: Option[Float]
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
    grammar: String,
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

  /** Executes the provided Odinson grammar.
    *
    * @param grammar An Odinson grammar
    * @param parentQuery A Lucene query to filter documents (optional).
    * @param pageSize The maximum number of sentences to execute the rules against.
    * @param allowTriggerOverlaps Whether or not event arguments are permitted to overlap with the event's trigger.
    * @return JSON of matches
    */
  def executeGrammar() = Action { request =>
    //println(s"body: ${request.body}")
    //val json: JsValue = request.body.asJson.get
    // FIXME: replace .get with validation check
    val gr = request.body.asJson.get.as[GrammarRequest]
    //println(s"GrammarRequest: ${gr}")
    val grammar = gr.grammar
    val parentQuery = gr.parentQuery
    val pageSize = gr.pageSize
    val allowTriggerOverlaps = gr.allowTriggerOverlaps.getOrElse(false)
    val pretty = gr.pretty
    try {
      // rules -> OdinsonQuery
      val baseExtractors = extractorEngine.ruleReader.compileRuleString(grammar)
      val composedExtractors = parentQuery match {
        case Some(pq) =>
          val cpq = extractorEngine.compiler.mkParentQuery(pq)
          baseExtractors.map(be =>
            be.copy(query = extractorEngine.compiler.mkQuery(be.query, cpq))
          )
        case None => baseExtractors
      }
      val start = System.currentTimeMillis()

      val maxSentences: Int = pageSize match {
        case Some(ps) => ps
        case None     => extractorEngine.numDocs()
      }

      val mentions: Seq[Mention] = {
        // FIXME: should deal in iterators to allow for, e.g., pagination...?
        val iterator = extractorEngine.extractMentions(
          composedExtractors,
          numSentences = maxSentences,
          allowTriggerOverlaps = allowTriggerOverlaps,
          disableMatchSelector = false
        )
        iterator.toVector
      }

      val duration =
        (System.currentTimeMillis() - start) / 1000f // duration in seconds

      val json = Json.toJson(mkJson(
        parentQuery,
        duration,
        allowTriggerOverlaps,
        mentions
      ))
      json.format(pretty)
    } catch {
      case NonFatal(e) =>
        val stackTrace = ExceptionUtils.getStackTrace(e)
        val json = Json.toJson(Json.obj("error" -> stackTrace))
        Status(400)(json)
    }
  }

  /** @param odinsonQuery An Odinson pattern
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
    label: Option[
      String
    ], // FIXME: in the future, this will be decided in the grammar
    commit: Option[
      Boolean
    ], // FIXME: in the future, this will be decided in the grammar
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
        val duration =
          (System.currentTimeMillis() - start) / 1000f // duration in seconds

        // should the results be added to the state?
        if (commit.getOrElse(false)) {
          // FIXME: can this be processed in the background?
          commitResults(
            odinsonQuery = odinsonQuery,
            parentQuery = parentQuery,
            label = label.getOrElse("Mention")
          )
        }

        val json = Json.toJson(mkJson(
          odinsonQuery,
          parentQuery,
          duration,
          results,
          enriched
        ))
        json.format(pretty)
      } catch {
        case NonFatal(e) =>
          val stackTrace = ExceptionUtils.getStackTrace(e)
          val json = Json.toJson(Json.obj("error" -> stackTrace))
          Status(400)(json)
      }
    }
  }

  def getMetadataJsonByDocumentId(
    documentId: String,
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      try {
        val od: OdinsonDocument = loadParentDocByDocumentId(documentId)
        val json: JsValue = Json.parse(od.toJson)("metadata")
        json.format(pretty)
      } catch {
        case NonFatal(e) =>
          val stackTrace = ExceptionUtils.getStackTrace(e)
          val json = Json.toJson(Json.obj("error" -> stackTrace))
          Status(400)(json)
      }
    }
  }

  def getMetadataJsonBySentenceId(
    sentenceId: Int,
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      try {
        val luceneDoc: LuceneDocument =
          extractorEngine.indexReader.document(sentenceId)
        val documentId = luceneDoc.getValues(DOC_ID_FIELD).head
        val od: OdinsonDocument = loadParentDocByDocumentId(documentId)
        val json: JsValue = Json.parse(od.toJson)("metadata")
        json.format(pretty)
      } catch {
        case NonFatal(e) =>
          val stackTrace = ExceptionUtils.getStackTrace(e)
          val json = Json.toJson(Json.obj("error" -> stackTrace))
          Status(400)(json)
      }
    }
  }

  def getParentDocJsonBySentenceId(
    sentenceId: Int,
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      try {
        val luceneDoc: LuceneDocument =
          extractorEngine.indexReader.document(sentenceId)
        val documentId = luceneDoc.getValues(DOC_ID_FIELD).head
        val od: OdinsonDocument = loadParentDocByDocumentId(documentId)
        val json: JsValue = Json.parse(od.toJson)
        json.format(pretty)
      } catch {
        case NonFatal(e) =>
          val stackTrace = ExceptionUtils.getStackTrace(e)
          val json = Json.toJson(Json.obj("error" -> stackTrace))
          Status(400)(json)
      }
    }
  }

  def getParentDocJsonByDocumentId(
    documentId: String,
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      try {
        val odinsonDoc = loadParentDocByDocumentId(documentId)
        val json: JsValue = Json.parse(odinsonDoc.toJson)
        json.format(pretty)
      } catch {
        case NonFatal(e) =>
          val stackTrace = ExceptionUtils.getStackTrace(e)
          val json = Json.toJson(Json.obj("error" -> stackTrace))
          Status(400)(json)
      }
    }
  }

  def mkJson(
    odinsonQuery: String,
    parentQuery: Option[String],
    duration: Float,
    results: OdinResults,
    enriched: Boolean
  ): JsValue = {

    val scoreDocs: JsValue = enriched match {
      case true =>
        Json.arr(results.scoreDocs.map(mkJsonWithEnrichedResponse): _*)
      case false => Json.arr(results.scoreDocs.map(mkJson): _*)
    }

    Json.obj(
      "odinsonQuery" -> odinsonQuery,
      "parentQuery" -> parentQuery,
      "duration" -> duration,
      "totalHits" -> results.totalHits,
      "scoreDocs" -> scoreDocs
    )
  }

  /** Process results from executeGrammar */
  def mkJson(
    parentQuery: Option[String],
    duration: Float,
    allowTriggerOverlaps: Boolean,
    mentions: Seq[Mention]
  ): JsValue = {

    val mentionsJson: JsValue = Json.arr(mentions.map(mkJson): _*)

    Json.obj(
      "parentQuery" -> parentQuery,
      "duration" -> duration,
      "allowTriggerOverlaps" -> allowTriggerOverlaps,
      "mentions" -> mentionsJson
    )
  }

  def mkJson(mention: Mention): Json.JsValueWrapper = {
    //val doc: LuceneDocument = extractorEngine.indexSearcher.doc(mention.luceneDocId)
    // We want **all** tokens for the sentence
    val tokens =
      extractorEngine.getTokens(mention.luceneDocId, WORD_TOKEN_FIELD)
    // odinsonMatch: OdinsonMatch,
    Json.obj(
      "sentenceId" -> mention.luceneDocId,
      // "score"         -> odinsonScoreDoc.score,
      "label" -> mention.label,
      "documentId" -> getDocId(mention.luceneDocId),
      "sentenceIndex" -> getSentenceIndex(mention.luceneDocId),
      "words" -> JsArray(tokens.map(JsString)),
      "foundBy" -> mention.foundBy,
      "match" -> Json.arr(mkJson(mention.odinsonMatch))
    )
  }

  def mkJson(odinsonScoreDoc: OdinsonScoreDoc): Json.JsValueWrapper = {
    //val doc = extractorEngine.indexSearcher.doc(odinsonScoreDoc.doc)
    // we want **all** tokens for the sentence
    val tokens =
      extractorEngine.getTokens(odinsonScoreDoc.doc, WORD_TOKEN_FIELD)
    Json.obj(
      "sentenceId" -> odinsonScoreDoc.doc,
      "score" -> odinsonScoreDoc.score,
      "documentId" -> getDocId(odinsonScoreDoc.doc),
      "sentenceIndex" -> getSentenceIndex(odinsonScoreDoc.doc),
      "words" -> JsArray(tokens.map(JsString)),
      "matches" -> Json.arr(odinsonScoreDoc.matches.map(mkJson): _*)
    )
  }

  def mkJson(m: OdinsonMatch): Json.JsValueWrapper = {
    Json.obj(
      "span" -> Json.obj("start" -> m.start, "end" -> m.end),
      "captures" -> Json.arr(m.namedCaptures.map(mkJson): _*)
    )
  }

  def mkJson(namedCapture: NamedCapture): Json.JsValueWrapper = {
    Json.obj(namedCapture.name -> mkJson(namedCapture.capturedMatch))
  }

  def mkJsonWithEnrichedResponse(odinsonScoreDoc: OdinsonScoreDoc)
    : Json.JsValueWrapper = {
    Json.obj(
      "sentenceId" -> odinsonScoreDoc.doc,
      "score" -> odinsonScoreDoc.score,
      "documentId" -> getDocId(odinsonScoreDoc.doc),
      "sentenceIndex" -> getSentenceIndex(odinsonScoreDoc.doc),
      "sentence" -> mkAbridgedSentence(odinsonScoreDoc.doc),
      "matches" -> Json.arr(odinsonScoreDoc.matches.map(mkJson): _*)
    )
  }

  def loadParentDocByDocumentId(documentId: String): OdinsonDocument = {
    //val doc = extractorEngine.indexSearcher.doc(odinsonScoreDoc.doc)
    //val fileName = doc.getField(fileName).stringValue
    // lucene doc containing metadata
    val parentDoc: LuceneDocument = extractorEngine.getParentDoc(documentId)
    val odinsonDocFile =
      new File(docsDir, parentDoc.getField("fileName").stringValue)
    OdinsonDocument.fromJson(odinsonDocFile)
  }

  def retrieveSentenceJson(documentId: String, sentenceIndex: Int): JsValue = {
    val odinsonDoc: OdinsonDocument = loadParentDocByDocumentId(documentId)
    val docJson: JsValue = Json.parse(odinsonDoc.toJson)
    (docJson \ "sentences")(sentenceIndex)
  }

  def mkAbridgedSentence(sentenceId: Int): JsValue = {
    val sentenceIndex = getSentenceIndex(sentenceId)
    val documentId = getDocId(sentenceId)
    val unabridgedJson = retrieveSentenceJson(documentId, sentenceIndex)
    unabridgedJson
    //unabridgedJson.as[JsObject] - "startOffsets" - "endOffsets" - "raw"
  }

}
