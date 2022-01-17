package ai.lum.odinson.lucene.index

import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.DisplayUtils._
import ai.lum.common.StringUtils._
import ai.lum.odinson.digraph.{ DirectedGraph, Vocabulary }
import ai.lum.odinson.lucene.analysis._
import ai.lum.odinson.serialization.UnsafeSerializer
import ai.lum.odinson.utils.IndexSettings
import ai.lum.odinson.utils.exceptions.OdinsonException
import ai.lum.odinson.{
  GraphField,
  Sentence,
  DateField => OdinsonDateField,
  Document => OdinsonDocument,
  Field => OdinsonField,
  NestedField => OdinsonNestedField,
  NumberField => OdinsonNumberField,
  StringField => OdinsonStringField,
  TokensField => OdinsonTokensField
}
import com.typesafe.config.{ Config, ConfigValueFactory }
import com.typesafe.scalalogging.LazyLogging
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.document.Field.Store
import org.apache.lucene.document.{
  BinaryDocValuesField => LuceneBinaryDocValuesField,
  Document => LuceneDocument,
  DoublePoint => LuceneDoublePoint,
  Field => LuceneField,
  NumericDocValuesField => LuceneNumericDocValuesField,
  StoredField => LuceneStoredField,
  StringField => LuceneStringField,
  TextField => LuceneTextField
}
import org.apache.lucene.index.IndexWriterConfig.OpenMode
import org.apache.lucene.index.{ DirectoryReader, IndexReader, IndexWriter, IndexWriterConfig }
import org.apache.lucene.store.{ Directory, FSDirectory, RAMDirectory }
import org.apache.lucene.util.BytesRef

import java.io.File
import java.nio.file.Paths
import java.util
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

class OdinsonIndexWriter(
  val writer: IndexWriter,
  val directory: Directory,
  val vocabulary: Vocabulary,
  val settings: IndexSettings,
  val normalizedTokenField: String,
  val addToNormalizedField: Set[String],
  val incomingTokenField: String,
  val outgoingTokenField: String,
  val maxNumberOfTokensPerSentence: Int,
  val invalidCharacterReplacement: String,
  val displayField: String
) extends LazyLogging {

  import ai.lum.odinson.lucene.index.OdinsonIndexWriter._

  def reader(): IndexReader = DirectoryReader.open(writer)

  def addDocuments(block: Seq[LuceneDocument]): Unit = {
    addDocuments(block.asJava)
  }

  def addDocuments(block: util.Collection[LuceneDocument]): Unit = {
    writer.addDocuments(block)
  }

  /** Add an Odinson Document to the index, where the Document is stored in a File.
    *
    * @param f         File with the Document
    * @param storeName whether to store the name of the file for retrieving later
    */
  def addFile(f: File, storeName: Boolean = true): Unit = {
    val origDoc = OdinsonDocument.fromJson(f)
    val block =
      if (storeName) {
        // keep track of file name to retrieve sentence JSON,
        // but ignore the path to the docs directory to avoid issues encountered when moving `odinson.dataDir`.
        // NOTE: this assumes all files are located immediately under `odinson.docsDir`
        // With large document collections, it may be necessary to split documents across many subdirectories
        // To avoid performance issues and limitations of certain file systems (ex. FAT32, ext2, etc.)
        val fileField: OdinsonField =
          OdinsonStringField(name = FILENAME_FIELD, string = f.getName)
        val doc = origDoc.copy(metadata = origDoc.metadata ++ Seq(fileField))
        mkDocumentBlock(doc)
      } else {
        mkDocumentBlock(origDoc)
      }
    // Add the document block
    addDocuments(block)
  }

  def commit(): Unit = writer.commit()

  def flush(): Unit = writer.flush()

  def close(): Unit = {
    flush()
    commit()
    writer.close()
  }

  /** Generates a sequence of [[org.apache.lucene.document.Document]] that represent an [[ai.lum.odinson.Document]] (metadata and sentences).
    *
    * @param d An Odinson Document.
    */
  def mkDocumentBlock(d: OdinsonDocument): Seq[LuceneDocument] = {
    val block = ArrayBuffer.empty[LuceneDocument]
    for ((s, i) <- d.sentences.zipWithIndex) {
      if (s.numTokens <= maxNumberOfTokensPerSentence) {
        block += mkSentenceDoc(s, d.id, i.toString)
      } else {
        logger.warn(s"skipping sentence with ${s.numTokens.display} tokens")
      }
    }
    // sentence docs, then the nested metadata documents, then the parent doc
    block ++ mkMetadataDocs(d)
  }

  /** Creates a sequence of [[org.apache.lucene.document.Document]] for the metadata of an [[ai.lum.odinson.Document]].
    *
    * @param s         An Odinson Sentence
    * @param docId The ID of the Odinson Document containing this Sentence.
    * @param sentId The ID for this Sentence.  Unique at the Odinson Document level.
    */
  def mkMetadataDocs(d: OdinsonDocument): Seq[LuceneDocument] = {
    val (nested, other) = d.metadata.partition(_.isInstanceOf[OdinsonNestedField])

    // convert the nested fields into a document
    val nestedMetadata = nested.collect { case n: OdinsonNestedField => n }.map(mkNestedDocument)

    // Metadata for parent document
    val metadata = new LuceneDocument
    metadata.add(new LuceneStringField(
      OdinsonIndexWriter.TYPE,
      OdinsonIndexWriter.PARENT_TYPE,
      Store.NO
    ))
    metadata.add(new LuceneStringField(DOC_ID_FIELD, d.id, Store.YES))

    for {
      odinsonField <- other
      luceneField <- mkLuceneFields(odinsonField, isMetadata = true)
    } metadata.add(luceneField)
    // NOTE: metadata must come last in the block 
    // for our block join query to match
    nestedMetadata ++ Seq(metadata)
  }

  /** Creates an [[org.apache.lucene.document.Document]] for an [[ai.lum.odinson.Sentence]].
    *
    * @param s         An Odinson Sentence
    * @param docId The ID of the Odinson Document containing this Sentence.
    * @param sentId The ID for this Sentence.  Unique at the Odinson Document level.
    */
  def mkSentenceDoc(s: Sentence, docId: String, sentId: String): LuceneDocument = {
    val sent = new LuceneDocument
    // add sentence metadata (odinson doc ID, etc)
    sent.add(new LuceneStoredField(DOC_ID_FIELD, docId))
    sent.add(new LuceneStoredField(SENT_ID_FIELD, sentId)) // FIXME should this be a number?
    sent.add(new LuceneNumericDocValuesField(SENT_LENGTH_FIELD, s.numTokens))
    // add fields
    for {
      odinsonField <- s.fields
      luceneField <- mkLuceneFields(odinsonField, s)
    } sent.add(luceneField)
    // add norm field
    val normFields = s.fields
      .collect { case f: OdinsonTokensField => f }
      .filter(f => addToNormalizedField.contains(f.name))
      .map(f => f.tokens)
      // Validate each of the strings in the internal sequence
      .map(validate)

    val tokenStream = new NormalizedTokenStream(normFields, aggressive = true)
    sent.add(new LuceneTextField(normalizedTokenField, tokenStream))
    // return sentence
    sent
  }

  /** returns a sequence of lucene fields corresponding to the provided odinson field */
  def mkLuceneFields(f: OdinsonField, s: Sentence): Seq[LuceneField] = {
    f match {
      case f: GraphField =>
        val incomingEdges = f.mkIncomingEdges(s.numTokens)
        val outgoingEdges = f.mkOutgoingEdges(s.numTokens)
        val roots = f.roots.toArray
        val in = new LuceneTextField(incomingTokenField, new DependencyTokenStream(incomingEdges))
        val out = new LuceneTextField(outgoingTokenField, new DependencyTokenStream(outgoingEdges))
        val bytes =
          UnsafeSerializer.graphToBytes(mkDirectedGraph(incomingEdges, outgoingEdges, roots))
        val graph = new LuceneBinaryDocValuesField(f.name, new BytesRef(bytes))
        Seq(graph, in, out)
      case f =>
        // fallback to the lucene fields that don't require sentence information
        // note that if we have a Sentence, it's never metadata
        mkLuceneFields(f, false)
    }
  }

  /** returns a sequence of lucene fields corresponding to the provided odinson field */
  def mkLuceneFields(f: OdinsonField, isMetadata: Boolean): Seq[LuceneField] = {
    val mustStore = settings.storedFields.contains(f.name)
    f match {
      // Separate StoredField because of Lucene API for DoublePoint:
      // https://lucene.apache.org/core/6_6_6/core/org/apache/lucene/document/DoublePoint.html
      case f: OdinsonDateField =>
        // todo: do we want more fields: String s'${f.name}.month'
        val fields = Seq(
          // the whole date
          new LuceneDoublePoint(f.name, f.localDate.toEpochDay),
          // just the year for simplifying queries (syntactic sugar)
          new LuceneDoublePoint(attributeName(f.name, YEAR), f.localDate.getYear)
        )
        if (mustStore) {
          val storedField = new LuceneStoredField(f.name, f.date)
          fields ++ Seq(storedField)
        } else {
          fields
        }

      case f: OdinsonNumberField =>
        // Separate StoredField because of Lucene API for DoublePoint:
        // https://lucene.apache.org/core/6_6_6/core/org/apache/lucene/document/DoublePoint.html
        val doubleField = new LuceneDoublePoint(f.name, f.value)
        if (mustStore) {
          val storedField = new LuceneStoredField(f.name, f.value)
          Seq(doubleField, storedField)
        } else {
          Seq(doubleField)
        }

      case f: OdinsonStringField =>
        if (isMetadata && !STRING_FIELD_EXCEPTIONS.contains(f.name))
          logger.info(STRING_FIELD_WARNING(f.name))
        val store = if (mustStore) Store.YES else Store.NO
        val string = f.string.normalizeUnicode
        val stringField = new LuceneStringField(f.name, string, store)
        Seq(stringField)

      case f: OdinsonTokensField =>
        val validated = validate(f.tokens)
        val tokens =
          if (isMetadata) {
            // for the metadata we want to casefold, remove diacritics etc.
            val normalized = validated.map(_.normalizeUnicodeAggressively)
            OdinsonIndexWriter.START_TOKEN +: normalized :+ OdinsonIndexWriter.END_TOKEN
          } else validated
        val tokenStream = new NormalizedTokenStream(Seq(tokens))
        val tokensField = new LuceneTextField(f.name, tokenStream)
        if (mustStore) {
          val text = validated.mkString(" ").normalizeUnicode
          val storedField = new LuceneStoredField(f.name, text)
          Seq(tokensField, storedField)
        } else {
          Seq(tokensField)
        }

      case _ => throw new OdinsonException(s"Unsupported Field: ${f.getClass}")
    }
  }

  def mkNestedDocument(nested: OdinsonNestedField): LuceneDocument = {
    val nestedMetadata = new LuceneDocument
    nestedMetadata.add(new LuceneStringField(OdinsonIndexWriter.NAME, nested.name, Store.NO))
    nestedMetadata.add(new LuceneStringField(
      OdinsonIndexWriter.TYPE,
      OdinsonIndexWriter.NESTED_TYPE,
      Store.NO
    ))

    for {
      odinsonField <- nested.fields
      // NestedField is always metadata
      luceneField <- mkLuceneFields(odinsonField, true)
    } nestedMetadata.add(luceneField)

    nestedMetadata
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
    *
    * @param s : String to be validated
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

  private def attributeName(field: String, attribute: String): String = s"$field.$attribute"

}

object OdinsonIndexWriter {

  val VOCABULARY_FILENAME = "dependencies.txt"
  val BUILDINFO_FILENAME = "buildinfo.json"
  val SETTINGSINFO_FILENAME = "settingsinfo.json"

  // Constants for Odinson Documents
  val DOC_ID_FIELD = "docId"
  val SENT_ID_FIELD = "sentId"
  val SENT_LENGTH_FIELD = "numWords"
  val FILENAME_FIELD = "fileName"

  // Constants for building the queries for metadata documents -- both the parent as well as the
  // nested metadata
  val TYPE = "type"
  val PARENT_TYPE = "metadata"
  val NESTED_TYPE = "metadata_nested"
  val NAME = "name"
  val YEAR = "year"

  // Special start and end tokens for metadata exact match queries
  val START_TOKEN = "[[XX_START]]"
  val END_TOKEN = "[[XX_END]]"

  // User-defined metadata cannot currently be a StringField.  That said, there are StringFields in the
  // metadata (the filename, etc.)
  val STRING_FIELD_EXCEPTIONS = Set(DOC_ID_FIELD, OdinsonIndexWriter.TYPE)

  def STRING_FIELD_WARNING(name: String) =
    s"Metadata StringField <${name}> will not be queryable using the metadata query language"

  protected[index] def fromConfig(): OdinsonIndexWriter = {
    fromConfig(ConfigFactory.load())
  }

  protected[index] def fromConfig(config: Config): OdinsonIndexWriter = {

    val indexDir = config.apply[String]("odinson.indexDir")
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

    val storedFields = config.apply[List[String]]("odinson.index.storedFields")
    val displayField = config.apply[String]("odinson.displayField")
    // Always store the display field, also store these additional fields
    if (!storedFields.contains(displayField)) {
      throw new OdinsonException("`odinson.index.storedFields` must contain `odinson.displayField`")
    }
    // see https://github.com/lum-ai/odinson/pull/337
    val writerConf = new IndexWriterConfig(new KeywordAnalyzer)
    writerConf.setOpenMode(OpenMode.CREATE_OR_APPEND)
    val writer = new IndexWriter(directory, writerConf)

    new OdinsonIndexWriter(
      writer = writer,
      directory = directory,
      vocabulary = vocabulary,
      settings = IndexSettings(storedFields),
      normalizedTokenField = config.apply[String]("odinson.index.normalizedTokenField"),
      addToNormalizedField = config.apply[List[String]]("odinson.index.addToNormalizedField").toSet,
      incomingTokenField = config.apply[String]("odinson.index.incomingTokenField"),
      outgoingTokenField = config.apply[String]("odinson.index.outgoingTokenField"),
      maxNumberOfTokensPerSentence =
        config.apply[Int]("odinson.index.maxNumberOfTokensPerSentence"),
      invalidCharacterReplacement =
        config.apply[String]("odinson.index.invalidCharacterReplacement"),
      displayField
    )
  }

  def inMemory(config: Config = ConfigFactory.load()): OdinsonIndexWriter = {
    // if the user wants the index to live in memory then we override the configuration
    val newConfig = config.withValue("odinson.indexDir", ConfigValueFactory.fromAnyRef(":memory:"))
    fromConfig(newConfig)
  }

}
