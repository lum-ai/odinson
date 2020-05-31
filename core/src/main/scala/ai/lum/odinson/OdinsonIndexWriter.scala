package ai.lum.odinson

import java.io.File
import java.nio.file.Paths
import java.util.Collection
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConverters._
import org.apache.lucene.util.BytesRef
import org.apache.lucene.{ document => lucenedoc }
import org.apache.lucene.document.Field.Store
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.index.{ IndexWriter, IndexWriterConfig }
import org.apache.lucene.index.IndexWriterConfig.OpenMode
import org.apache.lucene.store.{ Directory, FSDirectory, IOContext, RAMDirectory }
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.{ Config, ConfigValueFactory }
import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.StringUtils._
import ai.lum.common.DisplayUtils._
import ai.lum.common.TryWithResources.using
import ai.lum.odinson.lucene.analysis._
import ai.lum.odinson.digraph.{ DirectedGraph, Vocabulary }
import ai.lum.odinson.serialization.UnsafeSerializer

class OdinsonIndexWriter(
  val directory: Directory,
  val vocabulary: Vocabulary,
  val documentIdField: String,
  val sentenceIdField: String,
  val sentenceLengthField: String,
  val normalizedTokenField: String,
  val addToNormalizedField: Set[String],
  val incomingTokenField: String,
  val outgoingTokenField: String,
  val sortedDocValuesFieldMaxSize: Int,
  val maxNumberOfTokensPerSentence: Int,
) extends LazyLogging {

  import OdinsonIndexWriter._

  val analyzer = new WhitespaceAnalyzer()
  val writerConfig = new IndexWriterConfig(analyzer)
  writerConfig.setOpenMode(OpenMode.CREATE)
  val writer = new IndexWriter(directory, writerConfig)

  def addDocuments(block: Seq[lucenedoc.Document]): Unit = {
    addDocuments(block.asJava)
  }

  def addDocuments(block: Collection[lucenedoc.Document]): Unit = {
    writer.addDocuments(block)
  }

  def commit(): Unit = writer.commit()

  def close(): Unit = {
    // FIXME: is this the correct instantiation of IOContext?
    using (directory.createOutput(VOCABULARY_FILENAME, new IOContext)) { stream =>
      stream.writeString(vocabulary.dump)
    }
    using (directory.createOutput(BUILDINFO_FILENAME, new IOContext)) { stream =>
      stream.writeString(BuildInfo.toJson)
    }
    writer.close()
  }

  /** generates a lucenedoc document per sentence */
  def mkDocumentBlock(d: Document): Seq[lucenedoc.Document] = {
    val block = ArrayBuffer.empty[lucenedoc.Document]
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

  def mkMetadataDoc(d: Document): lucenedoc.Document = {
    val metadata = new lucenedoc.Document
    metadata.add(new lucenedoc.StringField("type", "metadata", Store.NO))
    metadata.add(new lucenedoc.StringField(documentIdField, d.id, Store.YES))
    for {
      odinsonField <- d.metadata
      luceneField <- mkLuceneFields(odinsonField)
    } metadata.add(luceneField)
    metadata
  }

  def mkSentenceDoc(s: Sentence, docId: String, sentId: String): lucenedoc.Document = {
    val sent = new lucenedoc.Document
    // add sentence metadata
    sent.add(new lucenedoc.StoredField(documentIdField, docId))
    sent.add(new lucenedoc.StoredField(sentenceIdField, sentId)) // FIXME should this be a number?
    sent.add(new lucenedoc.NumericDocValuesField(sentenceLengthField, s.numTokens))
    // add fields
    for {
      odinsonField <- s.fields
      luceneField <- mkLuceneFields(odinsonField, s)
    } sent.add(luceneField)
    // add norm field
    val normFields = s.fields
      .collect { case f: TokensField => f }
      .filter(f => addToNormalizedField.contains(f.name))
      .map(f => f.tokens)
    val tokenStream = new NormalizedTokenStream(normFields, aggressive = true)
    sent.add(new lucenedoc.TextField(normalizedTokenField, tokenStream))
    // return sentence
    sent
  }

  /** returns a sequence of lucene fields corresponding to the provided odinson field */
  def mkLuceneFields(f: Field, s: Sentence): Seq[lucenedoc.Field] = {
    f match {
      case f: GraphField =>
        val incomingEdges = f.mkIncomingEdges(s.numTokens)
        val outgoingEdges = f.mkOutgoingEdges(s.numTokens)
        val roots = f.roots.toArray
        val in  = new lucenedoc.TextField(incomingTokenField, new DependencyTokenStream(incomingEdges))
        val out = new lucenedoc.TextField(outgoingTokenField, new DependencyTokenStream(outgoingEdges))
        val bytes = UnsafeSerializer.graphToBytes(mkDirectedGraph(incomingEdges, outgoingEdges, roots))
        if (bytes.length <= sortedDocValuesFieldMaxSize) {
          val graph = new lucenedoc.SortedDocValuesField(f.name, new BytesRef(bytes))
          Seq(graph, in, out)
        } else {
          logger.warn(s"serialized dependencies too big for storage: ${bytes.length.display} > ${sortedDocValuesFieldMaxSize.display} bytes")
          Seq.empty
        }
      case f =>
        // fallback to the lucene fields that don't require sentence information
        mkLuceneFields(f)
    }
  }

  /** returns a sequence of lucene fields corresponding to the provided odinson field */
  def mkLuceneFields(f: Field): Seq[lucenedoc.Field] = f match {
    case f: DateField =>
      val longField = new lucenedoc.LongPoint(f.name, f.localDate.toEpochDay)
      if (f.store) {
        val storedField = new lucenedoc.StoredField(f.name, f.date)
        Seq(longField, storedField)
      } else {
        Seq(longField)
      }
    case f: StringField =>
      val store = if (f.store) Store.YES else Store.NO
      val string = f.string.normalizeUnicode
      val stringField = new lucenedoc.StringField(f.name, string, store)
      Seq(stringField)
    case f: TokensField if f.store =>
      val text = f.tokens.mkString(" ").normalizeUnicode
      val tokensField = new lucenedoc.TextField(f.name, text, Store.YES)
      Seq(tokensField)
    case f: TokensField =>
      val tokenStream = new NormalizedTokenStream(Seq(f.tokens))
      val tokensField = new lucenedoc.TextField(f.name, tokenStream)
      Seq(tokensField)
  }

  def mkDirectedGraph(
    incomingEdges: Array[Array[(Int, String)]],
    outgoingEdges: Array[Array[(Int, String)]],
    roots: Array[Int]
  ): DirectedGraph = {
    def toLabelIds(tokenEdges: Array[(Int, String)]): Array[Int] = for {
      (tok, label) <- tokenEdges
      labelId = vocabulary.getOrCreateId(label.normalizeUnicode)
      n <- Array(tok, labelId)
    } yield n
    val incoming = incomingEdges.map(toLabelIds)
    val outgoing = outgoingEdges.map(toLabelIds)
    DirectedGraph.mkGraph(incoming, outgoing, roots)
  }

}


object OdinsonIndexWriter {

  val VOCABULARY_FILENAME = "dependencies.txt"
  val BUILDINFO_FILENAME = "buildinfo.json"

  def fromConfig(): OdinsonIndexWriter = {
    fromConfig("odinson")
  }

  def fromConfig(path: String): OdinsonIndexWriter = {
    val config = ConfigFactory.load()
    fromConfig(config[Config](path))
  }

  def fromConfig(config: Config): OdinsonIndexWriter = {
    val indexDir = config[String]("indexDir")
    val documentIdField = config[String]("index.documentIdField")
    val sentenceIdField = config[String]("index.sentenceIdField")
    val sentenceLengthField  = config[String]("index.sentenceLengthField")
    val normalizedTokenField = config[String]("index.normalizedTokenField")
    val addToNormalizedField = config[List[String]]("index.addToNormalizedField")
    val incomingTokenField   = config[String]("index.incomingTokenField")
    val outgoingTokenField   = config[String]("index.outgoingTokenField")
    val sortedDocValuesFieldMaxSize  = config[Int]("index.sortedDocValuesFieldMaxSize")
    val maxNumberOfTokensPerSentence = config[Int]("index.maxNumberOfTokensPerSentence")
    val (directory, vocabulary) = indexDir match {
      case ":memory:" =>
        // memory index is supported in the configuration file
        val dir = new RAMDirectory
        val vocab = Vocabulary.empty
        (dir, vocab)
      case path =>
        val dir = FSDirectory.open(Paths.get(path))
        val vocab = Vocabulary.fromDirectory(dir)
        (dir, vocab)
    }
    new OdinsonIndexWriter(
      directory, vocabulary,
      documentIdField,
      sentenceIdField,
      sentenceLengthField,
      normalizedTokenField,
      addToNormalizedField.toSet,
      incomingTokenField,
      outgoingTokenField,
      sortedDocValuesFieldMaxSize,
      maxNumberOfTokensPerSentence,
    )
  }

  def inMemory: OdinsonIndexWriter = {
    val config = ConfigFactory.load()
    // if the user wants the index to live in memory then we override the configuration
    val newConfig = config.withValue("odinson.indexDir", ConfigValueFactory.fromAnyRef(":memory:"))
    fromConfig(newConfig[Config]("odinson"))
  }

}
