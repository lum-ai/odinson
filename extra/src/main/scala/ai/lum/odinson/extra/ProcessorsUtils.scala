package ai.lum.odinson.extra

import java.util.UUID
import org.clulab.processors.{
  Document => ProcessorsDocument,
  Sentence => ProcessorsSentence,
}
import ai.lum.odinson.{
  Document => OdinsonDocument,
  Sentence => OdinsonSentence,
  _
}
import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._

object ProcessorsUtils {

  // load field names from config
  val config = ConfigFactory.load()
  val documentIdField   = config[String]("odinson.index.documentIdField")
  val rawTokenField     = config[String]("odinson.index.rawTokenField")
  val wordTokenField    = config[String]("odinson.index.wordTokenField")
  val lemmaTokenField   = config[String]("odinson.index.lemmaTokenField")
  val posTagTokenField  = config[String]("odinson.index.posTagTokenField")
  val chunkTokenField   = config[String]("odinson.index.chunkTokenField")
  val entityTokenField  = config[String]("odinson.index.entityTokenField")
  val dependenciesField = config[String]("odinson.index.dependenciesField")

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
    val raw = TokensField(rawTokenField, s.raw, store = true)
    val word = TokensField(wordTokenField, s.words)
    val maybeTag = s.tags.map(tags => TokensField(posTagTokenField, tags))
    val maybeLemma = s.lemmas.map(lemmas => TokensField(lemmaTokenField, lemmas))
    val maybeEntity = s.entities.map(entities => TokensField(entityTokenField, entities))
    val maybeChunk = s.chunks.map(chunks => TokensField(chunkTokenField, chunks))
    val maybeDeps = s.dependencies.map(g => GraphField(dependenciesField, g.allEdges, g.roots))
    val fields = Some(raw) :: Some(word) :: List(maybeTag, maybeLemma, maybeEntity, maybeChunk, maybeDeps)
    OdinsonSentence(s.size, fields.flatten)
  }

}
