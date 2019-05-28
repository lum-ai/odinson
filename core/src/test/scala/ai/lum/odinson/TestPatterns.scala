package ai.lum.odinson

import org.scalatest._
import org.scalatest.prop.TableDrivenPropertyChecks._
import ai.lum.odinson.compiler.QueryCompiler
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.analysis.{ NormalizedTokenStream, OdinsonTokenStream, TokenStreamUtils }
import ai.lum.odinson.lucene.search._
import ai.lum.odinson.state.State
import ai.lum.odinson.utils.ConfigFactory
import ai.lum.common.ConfigUtils._
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.search.highlight.TokenSources
import org.apache.lucene.document.{ Document => LuceneDocument, _ }
import org.apache.lucene.document.Field.Store
import org.apache.lucene.index.DirectoryReader


class TestPatterns extends FlatSpec with Matchers {

  "patterns" should "match" in {
    val patterns = Table(
      ("pattern", "string", "expected"),
      ("a b c", "a b c", "a b c"),
      ("a b* c", "a b c", "a b c"),
      ("a b* c", "a b b c", "a b b c"),
      ("a b* c", "a c", "a c"),
      ("a b* b c", "a b c", "a b c"),
      ("a b* b c", "a b b c", "a b b c"),
      ("a b* b c", "a b b b c", "a b b b c"),
      ("a b* b c", "a b b b b c", "a b b b b c"),
      ("a b+ c", "a b c", "a b c"),
      ("a b+ c", "a b b c", "a b b c"),
      ("a b+ c ", "a b b b c", "a b b b c"),
      ("a b+ b c", "a b b c", "a b b c"),
      ("a b+ b c", "a b b b c", "a b b b c"),
      ("a b? b c", "a b c", "a b c"),
      ("a b? b c", "a b b c", "a b b c"),
      ("a b{0,1} c", "a c", "a c"),
      ("a b{0,1} c", "a b c", "a b c"),
      ("a (b | c) d", "a b d", "a b d"),
      ("a (b | c) d", "a c d", "a c d"),
      ("a (b | c)? d", "a d ", "a d"),
      ("a (b | c)? d", "a b d", "a b d"),
      ("a (b | c)? d", "a c d", "a c d"),
      ("a (b | c)* d", "a d", "a d"),
      ("a (b | c)* d", "a b d", "a b d"),
      ("a (b | c)* d", "a b c d", "a b c d"),
      ("a (b | c)* d", "a c b d", "a c b d"),
      ("a (b | c)* d", "a b b b d", "a b b b d"),
      ("a (b | c)* d", "a b c b c d", "a b c b c d"),
      ("a b | c d", "a b c", "a b"),
      ("a b | c d", "a c d", "c d"),
      ("a []*? c", "a b c a b c", "a b c"),
      ("a []* c", "a b c a b c", "a b c a b c"),
      ("a []+? c", "a b c a b c", "a b c"),
      ("a []+ c", "a b c a b c", "a b c a b c"),
      ("a []*? c", "a c b c a b c", "a c"),
      ("a []* c", "a c b c a b c", "a c b c a b c"),
      ("a []+? c", "a c b c a b c", "a c b c"),
      ("a []+ c", "a c b c a b c", "a c b c a b c"),
      ("(a+ | b)*", "a b", "a b"),
      ("(a+ | b){0,}", "a b", "a b"),
      ("(a+ | b)+", "a b", "a b"),
      ("(a+ | b){1,}", "a b", "a b"),
      ("(a+ | b)?", "a b", "a"),
      ("(a+ | b){0,1}", "a b", "a"),
      ("(a | b | c | d | e) f", "e f", "e f"),
      ("(a b | a b*) b c", "a b c", "a b c"),
    )
    forAll (patterns) { (pattern: String, string: String, expected: String) =>
      val ee = TestUtils.mkExtractorEngine(string)
      val results = ee.query(pattern)
      val actual = TestUtils.mkString(results, ee)
      actual should equal (expected)
    }
  }

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
    * Converts [[ai.lum.odinson.lucene.OdinResults]] to a string.  Used to compare actual to expected results.
    */
  def mkString(results: OdinResults, engine: ExtractorEngine): String = {
    if (results.totalHits == 0) {
      ""
    } else {
      val allMatchingSpans = for {
        scoreDoc <- results.scoreDocs
        tokens = engine.getTokens(scoreDoc)
        swc <- scoreDoc.matches
      } yield {
        tokens
          .slice(swc.span.start, swc.span.end)
          .mkString(" ")
      }
      allMatchingSpans.mkString(" ")
    }
  }

  /**
    * Constructs an [[ai.lum.odinson.ExtractorEngine]] from a single-doc in-memory index ([[org.apache.lucene.store.RAMDirectory]])
    */
  def mkExtractorEngine(text: String): ExtractorEngine = {

    val memWriter = OdinsonIndexWriter.inMemory
    writeDoc(memWriter, text)

    val reader = DirectoryReader.open(memWriter.directory)

    val indexSearcher = new OdinsonIndexSearcher(reader)
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
