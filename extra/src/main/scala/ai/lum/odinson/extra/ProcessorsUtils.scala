package ai.lum.odinson.extra

import java.util.UUID

import org.clulab.processors.{
  Document => ProcessorsDocument,
  Sentence => ProcessorsSentence
}
import ai.lum.odinson.{
  Document => OdinsonDocument,
  Sentence => OdinsonSentence,
  _
}
import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import edu.cmu.dynet.Initialize
import org.clulab.dynet.DyNetSync
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable

object ProcessorsUtils {

  private val logger: Logger = LoggerFactory.getLogger(ProcessorsUtils.getClass)

  // load field names from config
  val config = ConfigFactory.load()
  // format: off
  val documentIdField   = config[String]("odinson.index.documentIdField")
  val rawTokenField     = config[String]("odinson.index.rawTokenField")
  val wordTokenField    = config[String]("odinson.index.wordTokenField")
  val lemmaTokenField   = config[String]("odinson.index.lemmaTokenField")
  val posTagTokenField  = config[String]("odinson.index.posTagTokenField")
  val chunkTokenField   = config[String]("odinson.index.chunkTokenField")
  val entityTokenField  = config[String]("odinson.index.entityTokenField")
  val dependenciesField = config[String]("odinson.index.dependenciesField")
  // format: on

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
    val fields = Some(raw) :: Some(word) :: List(maybeTag, maybeLemma, maybeEntity, maybeChunk, maybeDeps)
    OdinsonSentence(s.size, fields.flatten)
  }

  // CluLab processors now uses dynet models, which need to be initialized at first
  // loading.  These variables and initialization method are for that process.
  val RANDOM_SEED = 2522620396L // used for both DyNet, and the JVM seed for shuffling data
  val WEIGHT_DECAY = 1e-5f

  private var IS_DYNET_INITIALIZED = false

  def initializeDyNet(autoBatch: Boolean = false, mem: String = ""): Unit = {
    DyNetSync.synchronized {
      if (!IS_DYNET_INITIALIZED) {
        logger.debug("Initializing DyNet...")

        val params = new mutable.HashMap[String, Any]()
        params += "random-seed" -> RANDOM_SEED
        params += "weight-decay" -> WEIGHT_DECAY
        if (autoBatch) {
          params += "autobatch" -> 1
          params += "dynet-mem" -> mem
        }

        Initialize.initialize(params.toMap)
        logger.debug("DyNet initialization complete.")
        IS_DYNET_INITIALIZED = true
      }
    }
  }

}
