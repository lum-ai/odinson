package ai.lum.odinson.extra

import java.io._
import scala.collection.mutable.ArrayBuffer
import scala.util.{ Try, Success, Failure }
import org.apache.lucene.util.BytesRef
import org.apache.lucene.document._
import org.apache.lucene.document.Field.Store
import com.typesafe.scalalogging.LazyLogging
import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.common.DisplayUtils._
import ai.lum.odinson
import ai.lum.odinson.lucene.analysis._
import ai.lum.odinson.OdinsonIndexWriter



object NewIndexDocuments extends App with LazyLogging {

  val config = ConfigFactory.load()
  val indexDir = config[File]("odinson.indexDir")
  val docsDir  = config[File]("odinson.docsDir")
  val documentIdField = config[String]("odinson.index.documentIdField")
  val sentenceIdField = config[String]("odinson.index.sentenceIdField")
  val sentenceLengthField  = config[String]("odinson.index.sentenceLengthField")
  val normalizedTokenField = config[String]("odinson.index.normalizedTokenField")
  val addToNormalized      = config[List[String]]("odinson.index.addToNormalized")
  val incomingTokenField   = config[String]("odinson.index.incomingTokenField")
  val outgoingTokenField   = config[String]("odinson.index.outgoingTokenField")
  val sortedDocValuesFieldMaxSize  = config[Int]("odinson.index.sortedDocValuesFieldMaxSize")
  val maxNumberOfTokensPerSentence = config[Int]("odinson.index.maxNumberOfTokensPerSentence")

  val writer = OdinsonIndexWriter.fromConfig

  // NOTE indexes the documents in parallel
  for {
    f <- docsDir.listFilesByWildcard("*.json", recursive = true).toSeq.par
  } {
    Try {
      val doc = odinson.Document.fromJson(f)
      val block = mkDocumentBlock(doc)
      writer.addDocuments(block)
    } match {
      case Success(_) =>
        logger.info(s"Indexed ${f.getName}")
      case Failure(e) =>
        logger.error(s"Failed to index ${f.getName}", e)
    }

  }

  writer.close
  // fin


  // generates a lucene document per sentence
  def mkDocumentBlock(d: odinson.Document): Seq[Document] = {
    val block = ArrayBuffer.empty[Document]
    for ((s, i) <- d.sentences.zipWithIndex) {
      if (s.numTokens <= maxNumberOfTokensPerSentence) {
        block += mkSentenceDoc(s, d.id, i.toString)
      } else {
        logger.warn(s"skipping sentence with ${s.numTokens.display} tokens")
      }
    }
    block += mkMetadataDoc(d)
    block
  }

  def mkMetadataDoc(d: odinson.Document): Document = {
    val metadata = new Document
    metadata.add(new StringField("type", "metadata", Store.NO))
    metadata.add(new StringField(documentIdField, d.id, Store.YES))
    for {
      odinsonField <- d.metadata
      luceneField <- mkLuceneFields(odinsonField)
    } metadata.add(luceneField)
    metadata
  }

  def mkSentenceDoc(s: odinson.Sentence, docId: String, sentId: String): Document = {
    val sent = new Document
    sent.add(new StoredField(documentIdField, docId))
    sent.add(new StoredField(sentenceIdField, sentId)) // FIXME should this be a number?
    sent.add(new NumericDocValuesField(sentenceLengthField, s.numTokens))
    for {
      odinsonField <- s.fields
      luceneField <- mkLuceneFields(odinsonField)
    } sent.add(luceneField)
    val normFields = s.fields
      .collect { case f: odinson.TokensField => f }
      .filter(f => addToNormalized.contains(f.name))
      .map(f => f.tokens)
    sent.add(new TextField(normalizedTokenField, new NormalizedTokenStream(normFields)))
    sent
  }

  def mkLuceneFields(f: odinson.Field): Seq[Field] = {
    f match {
      case f: odinson.StringField =>
        val store = if (f.store) Store.YES else Store.NO
        val stringField = new StringField(f.name, f.string, store)
        Seq(stringField)
      case f: odinson.TokensField if f.store =>
        val tokensField = new TextField(f.name, f.tokens.mkString(" "), Store.YES)
        Seq(tokensField)
      case f: odinson.TokensField =>
        val tokensField = new TextField(f.name, new OdinsonTokenStream(f.tokens))
        Seq(tokensField)
      case f: odinson.GraphField =>
        val in  = new TextField(incomingTokenField, new DependencyTokenStream(f.incomingEdges))
        val out = new TextField(outgoingTokenField, new DependencyTokenStream(f.outgoingEdges))
        val bytes = writer.mkDirectedGraph(f).toBytes
        if (bytes.length <= sortedDocValuesFieldMaxSize) {
          val graph = new SortedDocValuesField(f.name, new BytesRef(bytes))
          Seq(graph, in, out)
        } else {
          logger.warn(s"serialized dependencies too big for storage: ${bytes.length.display} > ${sortedDocValuesFieldMaxSize.display} bytes")
          Seq.empty
        }
    }
  }

}
