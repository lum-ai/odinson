package ai.lum.odinson.extra

import java.io._
import java.nio.file.Path
import java.util.Collection

import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConverters._
import org.apache.lucene.util.BytesRef
import org.apache.lucene.index.{ IndexWriter, IndexWriterConfig }
import org.apache.lucene.index.IndexWriterConfig.OpenMode
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.document.{ Document, StoredField, TextField }
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.NumericDocValuesField
import org.apache.lucene.document.SortedDocValuesField
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.clulab.struct.{ DirectedGraph => ProcessorsDirectedGraph }
import org.clulab.processors.{ Sentence, Document => ProcessorsDocument }
import org.clulab.serialization.json.JSONSerializer
import org.json4s._
import org.json4s.jackson.JsonMethods._
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.common.Serializer
import ai.lum.odinson.lucene.analysis._
import ai.lum.odinson.digraph.{ DirectedGraph, Vocabulary }

import scala.util.{ Failure, Success, Try }


object IndexDocuments extends App with LazyLogging {

  val config = ConfigFactory.load()
  val indexDir = config[Path]("odinson.indexDir")
  val docsDir  = config[File]("odinson.docsDir")
  val documentIdField = config[String]("odinson.index.documentIdField")
  val sentenceIdField = config[String]("odinson.index.sentenceIdField")
  val sentenceLengthField  = config[String]("odinson.index.sentenceLengthField")
  val rawTokenField        = config[String]("odinson.index.rawTokenField")
  val wordTokenField       = config[String]("odinson.index.wordTokenField")
  val normalizedTokenField = config[String]("odinson.index.normalizedTokenField")
  // TODO lowerCaseTokenField is now deprecated and should be removed ASAP
  val lowerCaseTokenField  = config[String]("odinson.index.lowerCaseTokenField")
  val lemmaTokenField      = config[String]("odinson.index.lemmaTokenField")
  val posTagTokenField     = config[String]("odinson.index.posTagTokenField")
  val chunkTokenField      = config[String]("odinson.index.chunkTokenField")
  val entityTokenField     = config[String]("odinson.index.entityTokenField")
  val incomingTokenField   = config[String]("odinson.index.incomingTokenField")
  val outgoingTokenField   = config[String]("odinson.index.outgoingTokenField")
  val dependenciesField    = config[String]("odinson.index.dependenciesField")
  val dependenciesVocabularyFile   = config[File]("odinson.index.dependenciesVocabulary")
  val sortedDocValuesFieldMaxSize  = config[Int]("odinson.index.sortedDocValuesFieldMaxSize")
  val maxNumberOfTokensPerSentence = config[Int]("odinson.index.maxNumberOfTokensPerSentence")

  implicit val formats = DefaultFormats

  // we will populate this vocabulary with the dependencies
  // and we will dump it at the end
  val dependenciesVocabulary = Vocabulary.empty

  val dir = FSDirectory.open(indexDir)
  val analyzer = new WhitespaceAnalyzer()
  val writerConfig = new IndexWriterConfig(analyzer)
  writerConfig.setOpenMode(OpenMode.CREATE)
  val writer = new IndexWriter(dir, writerConfig)

  // serialized org.clulab.processors.Document or Document json
  val SUPPORTED_EXTENSIONS = "(?i).*?\\.(ser|json)$"
  // NOTE indexes the documents in parallel
  for (f <- docsDir.listFilesByRegex(SUPPORTED_EXTENSIONS, recursive = true).toSeq.par) {
    Try {
      val doc = deserializeDoc(f)
      val block = mkDocumentBlock(doc)
      writer.addDocuments(block)
    } match {
      case Success(_) =>
        logger.info(s"Indexed ${f.getName}")
      case Failure(e) =>
        logger.error(s"Failed to index ${f.getName}", e)
    }

  }

  dependenciesVocabulary.dumpToFile(dependenciesVocabularyFile)
  writer.close()

  // fin



  def deserializeDoc(f: File): ProcessorsDocument = f.getName.toLowerCase match {
    case json if json.endsWith(".json") => JSONSerializer.toDocument(f)
    case ser if ser.endsWith(".ser") => Serializer.deserialize[ProcessorsDocument](f)
      // NOTE: we're assuming this is
    case gz if gz.endsWith("json.gz") =>
      val contents: String = GzipUtils.uncompress(f)
      val jast = parse(contents)
      JSONSerializer.toDocument(jast)
    case other =>
      throw new Exception(s"Cannot deserialize ${f.getName} to org.clulab.processors.Document. Unsupported extension '$other'")
  }

  def generateUUID: String = {
    java.util.UUID.randomUUID().toString
  }

  // generates a lucene document per sentence
  def mkDocumentBlock(d: ProcessorsDocument): Collection[Document] = {
    // FIXME what should we do if the document has no id?
    val docId = d.id.getOrElse(generateUUID)

    val block = ArrayBuffer.empty[Document]
    for ((s, i) <- d.sentences.zipWithIndex) {
      if (s.size <= maxNumberOfTokensPerSentence) {
        block += mkSentenceDoc(s, docId, i.toString)
      } else {
        logger.warn(s"skipping sentence with ${s.size} tokens")
      }
    }
    block += mkParentDoc(docId)
    block.toSeq.asJava
  }

  def mkParentDoc(docId: String): Document = {
    val parent = new Document
    parent.add(new TextField("type", "root", Store.NO))
    parent.add(new StoredField("docId", docId))
    parent
  }

  def mkSentenceDoc(s: Sentence, docId: String, sentId: String): Document = {
    val sent = new Document
    sent.add(new StoredField(documentIdField, docId))
    sent.add(new StoredField(sentenceIdField, sentId))
    sent.add(new NumericDocValuesField(sentenceLengthField, s.size))
    sent.add(new TextField(rawTokenField, new OdinsonTokenStream(s.raw)))
    sent.add(new TextField(wordTokenField, new OdinsonTokenStream(s.words)))
    sent.add(new TextField(normalizedTokenField, new NormalizedTokenStream(s.raw, s.words)))
    sent.add(new TextField(lowerCaseTokenField, new OdinsonTokenStream(s.words.map(_.toLowerCase))))
    if (s.tags.isDefined) {
      sent.add(new TextField(posTagTokenField, new OdinsonTokenStream(s.tags.get)))
    }
    if (s.lemmas.isDefined) {
      sent.add(new TextField(lemmaTokenField, new OdinsonTokenStream(s.lemmas.get)))
    }
    if (s.entities.isDefined) {
      sent.add(new TextField(entityTokenField, new OdinsonTokenStream(s.entities.get)))
    }
    if (s.chunks.isDefined) {
      sent.add(new TextField(chunkTokenField, new OdinsonTokenStream(s.chunks.get)))
    }
    if (s.dependencies.isDefined) {
      val deps = s.dependencies.get
      sent.add(new TextField(incomingTokenField, new DependencyTokenStream(deps.incomingEdges)))
      sent.add(new TextField(outgoingTokenField, new DependencyTokenStream(deps.outgoingEdges)))
      val bytes = mkDirectedGraph(deps, dependenciesVocabulary).toBytes
      if (bytes.length <= sortedDocValuesFieldMaxSize) {
        sent.add(new SortedDocValuesField(dependenciesField, new BytesRef(bytes)))
      } else {
        logger.warn(s"serialized dependencies too big for storage: ${bytes.length} > $sortedDocValuesFieldMaxSize bytes")
      }
    }
    sent
  }

  def textfield(name: String, tokens: Seq[String], store: Store = Store.NO): TextField = {
    new TextField(name, tokens.mkString(" "), store)
  }

  def textfield(name: String, deps: Array[Array[(Int, String)]]): TextField = {
    new TextField(name, new DependencyTokenStream(deps))
  }

  def mkDirectedGraph(g: ProcessorsDirectedGraph[String], v: Vocabulary): DirectedGraph = {
    def toLabelIds(tokenEdges: Array[(Int, String)]): Array[Int] = for {
      (tok, label) <- tokenEdges
      labelId = v.getOrCreateId(label)
      n <- Array(tok, labelId)
    } yield n
    val incoming = g.incomingEdges.map(toLabelIds)
    val outgoing = g.outgoingEdges.map(toLabelIds)
    DirectedGraph(incoming, outgoing, g.roots.toArray)
  }

}
