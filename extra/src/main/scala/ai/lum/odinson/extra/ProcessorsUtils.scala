package ai.lum.odinson.extra

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
    val metadata = mkMetadata(d)
    val sentences = mkSentences(d)
    OdinsonDocument(metadata, sentences)
  }

  /** generate metadata from processors document */
  def mkMetadata(d: ProcessorsDocument): Seq[Field] = {
    val maybeDocId = d.id.map(id => StringField(documentIdField, id, store = true))
    // TODO add more metadata
    val metadata = Seq(maybeDocId)
    metadata.flatten
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
    val maybeDeps = s.dependencies.map(deps => GraphField(dependenciesField, convertEdges(deps.incomingEdges), convertEdges(deps.outgoingEdges), deps.roots))
    val fields = Some(raw) :: Some(word) :: List(maybeTag, maybeLemma, maybeEntity, maybeChunk, maybeDeps)
    OdinsonSentence(fields.flatten)
  }

  /** convert edges type */
  private def convertEdges(edges: Array[Array[(Int, String)]]): Seq[Seq[(Int, String)]] = {
    edges.map(_.toSeq).toSeq
  }

}
