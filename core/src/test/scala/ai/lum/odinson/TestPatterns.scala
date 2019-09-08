package ai.lum.odinson

import org.scalatest._
import ai.lum.odinson.compiler.QueryCompiler
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search._
import ai.lum.odinson.state.State
import ai.lum.odinson.utils.ConfigFactory
import ai.lum.common.ConfigUtils._
import org.apache.lucene.document.{ Document => LuceneDocument, _ }
import org.apache.lucene.document.Field.Store
import org.apache.lucene.index.DirectoryReader


class TestPatterns extends FlatSpec with Matchers {

  val patternFile = "patternsThatMatch.tsv"
  val source = scala.io.Source.fromResource(patternFile)
  val lines = source.getLines().toArray

  for (line <- lines.drop(1)) { // skip header
    val Array(pattern, string, allExpected) = line.trim.split("\t")
    val expected = allExpected.split(";", -1)
    pattern should s"find all expected results for «$string»" in {
      val ee = TestUtils.mkExtractorEngine(string)
      val results = ee.query(pattern)
      val actual = TestUtils.mkStrings(results, ee)
      actual should equal (expected)
    }
  }

  source.close()

}

object TestUtils {

  val config = ConfigFactory.load()
  val allTokenFields       = config[List[String]]("odinson.compiler.allTokenFields")
  val documentIdField      = config[String]("odinson.index.documentIdField")
  val sentenceIdField      = config[String]("odinson.index.sentenceIdField")
  val sentenceLengthField  = config[String]("odinson.index.sentenceLengthField")
  val rawTokenField        = config[String]("odinson.index.rawTokenField")
  val normalizedTokenField = config[String]("odinson.index.normalizedTokenField")
  val wordTokenField       = config[String]("odinson.index.wordTokenField")

  val defaultTokenField    = config[String]("odinson.compiler.defaultTokenField")
  val dependenciesField    = config[String]("odinson.compiler.dependenciesField")
  val incomingTokenField   = config[String]("odinson.compiler.incomingTokenField")
  val outgoingTokenField   = config[String]("odinson.compiler.outgoingTokenField")

  val normalizeQueriesToDefaultField = config[Boolean]("odinson.compiler.normalizeQueriesToDefaultField")

  /**
    * Converts [[ai.lum.odinson.lucene.OdinResults]] to an array of strings.
    * Used to compare actual to expected results.
    */
  def mkStrings(results: OdinResults, engine: ExtractorEngine): Array[String] = {
    for {
      scoreDoc <- results.scoreDocs
      tokens = engine.getTokens(scoreDoc)
      swc <- scoreDoc.matches
    } yield {
      tokens
        .slice(swc.span.start, swc.span.end)
        .mkString(" ")
    }
  }

  /**
    * Constructs an [[ai.lum.odinson.ExtractorEngine]] from a single-doc in-memory index ([[org.apache.lucene.store.RAMDirectory]])
    */
  def mkExtractorEngine(text: String): ExtractorEngine = {

    val memWriter = OdinsonIndexWriter.inMemory
    writeDoc(memWriter, text)

    val reader = DirectoryReader.open(memWriter.directory)
    val indexSearcher = new OdinsonIndexSearcher(reader, computeTotalHits = true)
    val compiler = new QueryCompiler(
      allTokenFields = allTokenFields,
      defaultTokenField = rawTokenField, // raw is the default field for testing purposes
      sentenceLengthField = sentenceLengthField,
      dependenciesField = dependenciesField,
      incomingTokenField = incomingTokenField,
      outgoingTokenField = outgoingTokenField,
      dependenciesVocabulary = memWriter.vocabulary,
      normalizeQueriesToDefaultField = normalizeQueriesToDefaultField
    )

    val jdbcUrl = config[String]("odinson.state.jdbc.url")
    val state = new State(jdbcUrl)
    state.init()
    compiler.setState(state)
    new ExtractorEngine(indexSearcher, compiler, state, documentIdField)

  }

  /**
    * Writes string to in-memory index ([[org.apache.lucene.store.RAMDirectory]]), and commits result.
    */
  def writeDoc(writer: OdinsonIndexWriter, text: String): Unit = {
    val doc = mkSentenceDoc(text)
    writer.addDocuments(Seq(doc))
    writer.commit()
  }

  /**
    * Creates a simple Odison-style [[org.apache.lucene.document.Document]] from a space-delimited string.
    */
  def mkSentenceDoc(text: String): LuceneDocument = {
    val toks: Array[String] = text.split(" ")
    val sent = new LuceneDocument
    sent.add(new StoredField(documentIdField, "doc-1"))
    sent.add(new StoredField(sentenceIdField, "sent-1"))
    sent.add(new NumericDocValuesField(sentenceLengthField, toks.size.toLong))
    sent.add(new TextField(rawTokenField, text, Store.YES))
    sent
  }

}
