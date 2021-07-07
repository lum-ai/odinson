package ai.lum.odinson.extra

import java.util.UUID

import org.clulab.processors.{
  Processor,
  Document => ProcessorsDocument,
  Sentence => ProcessorsSentence
}
import ai.lum.odinson.{ Document => OdinsonDocument, Sentence => OdinsonSentence, _ }
import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import org.clulab.dynet.Utils.initializeDyNet
import org.clulab.processors.clu.CluProcessor
import org.clulab.processors.fastnlp.FastNLPProcessor
import org.slf4j.{ Logger, LoggerFactory }

import scala.collection.mutable

object ProcessorsUtils {

  private val logger: Logger = LoggerFactory.getLogger(ProcessorsUtils.getClass)

  // load field names from config
  val config = ConfigFactory.load()
  // format: off
  val rawTokenField     = config.apply[String]("odinson.index.rawTokenField")
  val wordTokenField    = config.apply[String]("odinson.index.wordTokenField")
  val lemmaTokenField   = config.apply[String]("odinson.index.lemmaTokenField")
  val posTagTokenField  = config.apply[String]("odinson.index.posTagTokenField")
  val chunkTokenField   = config.apply[String]("odinson.index.chunkTokenField")
  val entityTokenField  = config.apply[String]("odinson.index.entityTokenField")
  val dependenciesField = config.apply[String]("odinson.index.dependenciesField")
  // format: on

  def getProcessor(processorType: String): Processor = {
    processorType match {
      case "FastNLPProcessor" => {
        initializeDyNet()
        new FastNLPProcessor
      }
      case "CluProcessor" => {
        initializeDyNet()
        new CluProcessor
      }
    }
  }

  /** convert processors document to odinson document */
  def convertDocument(d: ProcessorsDocument): OdinsonDocument = {
    val id = d.id.getOrElse(UUID.randomUUID.toString)
    val metadata = mkMetadata(d)
    val sentences = mkSentences(d)
    OdinsonDocument(id, metadata, sentences)
  }

  /** generate metadata from processors document */
  def mkMetadata(d: ProcessorsDocument): Seq[Field] = {
    // TODO return metadata
    Seq.empty
  }

  /** make sequence of odinson documents from processors document */
  def mkSentences(d: ProcessorsDocument): Seq[OdinsonSentence] = {
    d.sentences.map(convertSentence)
  }

  /** convert processors sentence to odinson sentence */
  def convertSentence(s: ProcessorsSentence): OdinsonSentence = {
    val raw = TokensField(rawTokenField, s.raw)
    val word = TokensField(wordTokenField, s.words)
    val maybeTag = s.tags.map(tags => TokensField(posTagTokenField, tags))
    val maybeLemma = s.lemmas.map(lemmas => TokensField(lemmaTokenField, lemmas))
    val maybeEntity = s.entities.map(entities => TokensField(entityTokenField, entities))
    val maybeChunk = s.chunks.map(chunks => TokensField(chunkTokenField, chunks))
    val maybeDeps = s.dependencies.map(g => GraphField(dependenciesField, g.allEdges, g.roots))
    val fields =
      Some(raw) :: Some(word) :: List(maybeTag, maybeLemma, maybeEntity, maybeChunk, maybeDeps)
    OdinsonSentence(s.size, fields.flatten)
  }

}
