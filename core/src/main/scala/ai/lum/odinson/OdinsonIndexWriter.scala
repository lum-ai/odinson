package ai.lum.odinson

import java.io.File
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
import ai.lum.odinson.utils.IndexSettings
import ai.lum.odinson.utils.exceptions.OdinsonException
import org.apache.lucene.document.BinaryDocValuesField
import ai.lum.odinson.{ Document => OdinsonDocument }

class OdinsonIndexWriter(
  val directory: Directory,
  val vocabulary: Vocabulary,
  val settings: IndexSettings,
  val documentIdField: String,
  val sentenceIdField: String,
  val sentenceLengthField: String,
  val normalizedTokenField: String,
  val addToNormalizedField: Set[String],
  val incomingTokenField: String,
  val outgoingTokenField: String,
  val maxNumberOfTokensPerSentence: Int,
  val invalidCharacterReplacement: String,
  val displayField: String,
  val incremental: Boolean = false
) extends LazyLogging {

  import OdinsonIndexWriter._

  val analyzer = new WhitespaceAnalyzer()

  val writerConfig = {
    val writerConf = new IndexWriterConfig(analyzer)
    if (incremental) writerConf.setOpenMode(OpenMode.CREATE_OR_APPEND)
    else writerConf.setOpenMode(OpenMode.CREATE)
    writerConf
  }

  val writer = new IndexWriter(directory, writerConfig)

  def addDocument(doc: OdinsonDocument): Unit = {
    indexDocuments(mkDocumentBlock(doc))
  }

  def addDocuments(block: Seq[lucenedoc.Document]): Unit = {
    addDocuments(block.asJava)
  }

  def addDocuments(block: Collection[lucenedoc.Document]): Unit = {
    writer.addDocuments(block)
  }

  def indexDocuments(block: Seq[lucenedoc.Document]): Unit = {
    indexDocuments(block.asJava)
  }

  def indexDocuments(block: Collection[lucenedoc.Document]): Unit = {
    addDocuments(block)
    if (incremental) commit()
  }

  /** Add an Odinson Document to the index, where the Document is stored in a File.
    * @param f File with the Document
    * @param storeName whether to store the name of the file for retrieving later
    */
  def addFile(f: File, storeName: Boolean = true): Unit = {
    val origDoc = Document.fromJson(f)
    val block =
      if (storeName) {
        // keep track of file name to retrieve sentence JSON,
        // but ignore the path to the docs directory to avoid issues encountered when moving `odinson.dataDir`.
        // NOTE: this assumes all files are located immediately under `odinson.docsDir`
        // With large document collections, it may be necessary to split documents across many subdirectories
        // To avoid performance issues and limitations of certain file systems (ex. FAT32, ext2, etc.)
        val fileField: Field =
          StringField(name = "fileName", string = f.getName)
        val doc = origDoc.copy(metadata = origDoc.metadata ++ Seq(fileField))
        mkDocumentBlock(doc)
      } else {
        mkDocumentBlock(origDoc)
      }
    // Add the document block
    addDocuments(block)
  }

  def commit(): Unit = {
    writer.flush()
    writer.commit()
  }

  def close(): Unit = {
    // the Lucene API will not let you overwrite or merge existing files, so we need to rewrite the files with updated content before the writer is closed
    if (directory.listAll().contains(VOCABULARY_FILENAME)) directory.deleteFile(VOCABULARY_FILENAME)
    if (directory.listAll().contains(BUILDINFO_FILENAME)) directory.deleteFile(BUILDINFO_FILENAME)
    if (directory.listAll().contains(SETTINGSINFO_FILENAME))
      directory.deleteFile(SETTINGSINFO_FILENAME)

    // FIXME: is this the correct instantiation of IOContext?
    using(directory.createOutput(VOCABULARY_FILENAME, new IOContext)) { stream =>
      stream.writeString(vocabulary.dump)
    }
    using(directory.createOutput(BUILDINFO_FILENAME, new IOContext)) { stream =>
      stream.writeString(BuildInfo.toJson)
    }
    using(directory.createOutput(SETTINGSINFO_FILENAME, new IOContext)) { stream =>
      stream.writeString(settings.dump)
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
      // Validate each of the strings in the internal sequence
      .map(validate)

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
        val in =
          new lucenedoc.TextField(incomingTokenField, new DependencyTokenStream(incomingEdges))
        val out =
          new lucenedoc.TextField(outgoingTokenField, new DependencyTokenStream(outgoingEdges))
        val bytes =
          UnsafeSerializer.graphToBytes(mkDirectedGraph(incomingEdges, outgoingEdges, roots))
        val graph = new BinaryDocValuesField(f.name, new BytesRef(bytes))
        Seq(graph, in, out)
      case f =>
        // fallback to the lucene fields that don't require sentence information
        mkLuceneFields(f)
    }
  }

  /** returns a sequence of lucene fields corresponding to the provided odinson field */
  def mkLuceneFields(f: Field): Seq[lucenedoc.Field] = {
    val mustStore = settings.storedFields.contains(f.name)
    f match {
      case f: DateField =>
        val longField = new lucenedoc.LongPoint(f.name, f.localDate.toEpochDay)
        if (mustStore) {
          val storedField = new lucenedoc.StoredField(f.name, f.date)
          Seq(longField, storedField)
        } else {
          Seq(longField)
        }
      case f: StringField =>
        val store = if (mustStore) Store.YES else Store.NO
        val string = f.string.normalizeUnicode
        val stringField = new lucenedoc.StringField(f.name, string, store)
        Seq(stringField)
      case f: TokensField if mustStore =>
        val validatedTokens = validate(f.tokens)
        val text = validatedTokens.mkString(" ").normalizeUnicode
        val tokensField = new lucenedoc.TextField(f.name, text, Store.YES)
        Seq(tokensField)
      case f: TokensField =>
        // Make sure there are no invalid tokens
        val validated = validate(f.tokens)
        val tokenStream = new NormalizedTokenStream(Seq(validated))
        val tokensField = new lucenedoc.TextField(f.name, tokenStream)
        Seq(tokensField)
    }
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

  // ---------------------------------
  //         Helper Methods
  // ---------------------------------

  /** Validate a string, replacing it if invalid.
    * @param s: String to be validated
    * @return valid String (original or replaced)
    */
  private def validate(s: String): String = {
    // One day we may want more things here, for now, we're just replacing
    // characters that are problematic for Lucene
    replaceControlCharacterString(s)
  }

  // Helper method to apply the validation to each String in a sequence
  private def validate(ss: Seq[String]): Seq[String] = ss.map(validate)

  private def replaceControlCharacterString(s: String): String = {
    // If a token consists entirely of whitespace (e.g., record separator), replace it
    if (s.isWhitespace) invalidCharacterReplacement
    else s
  }

}

object OdinsonIndexWriter {

  val VOCABULARY_FILENAME = "dependencies.txt"
  val BUILDINFO_FILENAME = "buildinfo.json"
  val SETTINGSINFO_FILENAME = "settingsinfo.json"

  def fromConfig(): OdinsonIndexWriter = {
    fromConfig(ConfigFactory.load())
  }

  def fromConfig(config: Config): OdinsonIndexWriter = {
    val indexDir = config[String]("odinson.indexDir")
    val documentIdField = config[String]("odinson.index.documentIdField")
    val sentenceIdField = config[String]("odinson.index.sentenceIdField")
    val sentenceLengthField = config[String]("odinson.index.sentenceLengthField")
    val normalizedTokenField = config[String]("odinson.index.normalizedTokenField")
    val addToNormalizedField = config[List[String]]("odinson.index.addToNormalizedField")
    val incomingTokenField = config[String]("odinson.index.incomingTokenField")
    val outgoingTokenField = config[String]("odinson.index.outgoingTokenField")
    val maxNumberOfTokensPerSentence = config[Int]("odinson.index.maxNumberOfTokensPerSentence")
    val invalidCharacterReplacement = config[String]("odinson.index.invalidCharacterReplacement")
    val storedFields = config[List[String]]("odinson.index.storedFields")
    val displayField = config[String]("odinson.displayField")
    val (directory, vocabulary) = indexDir match {
      case ":memory:" =>
        // memory index is supported in the configuration file
        val dir = new RAMDirectory
        val vocab = Vocabulary.empty
        (dir, vocab)
      case path =>
        val dir = FSDirectory.open(new File(path).toPath)
        val vocab = Vocabulary.fromDirectory(dir)
        (dir, vocab)
    }
    // Always store the display field, also store these additional fields
    if (!storedFields.contains(displayField)) {
      throw new OdinsonException("`odinson.index.storedFields` must contain `odinson.displayField`")
    }

    val incremental = config[Boolean]("odinson.index.incremental")
    val settings = IndexSettings(storedFields)
    new OdinsonIndexWriter(
      directory,
      vocabulary,
      settings,
      documentIdField,
      sentenceIdField,
      sentenceLengthField,
      normalizedTokenField,
      addToNormalizedField.toSet,
      incomingTokenField,
      outgoingTokenField,
      maxNumberOfTokensPerSentence,
      invalidCharacterReplacement,
      displayField,
      incremental
    )
  }

  def inMemory: OdinsonIndexWriter = {
    val config = ConfigFactory.load()
    inMemory(config)
  }

  def inMemory(config: Config): OdinsonIndexWriter = {
    // if the user wants the index to live in memory then we override the configuration
    val newConfig = config.withValue("odinson.indexDir", ConfigValueFactory.fromAnyRef(":memory:"))
    fromConfig(newConfig)
  }

}
