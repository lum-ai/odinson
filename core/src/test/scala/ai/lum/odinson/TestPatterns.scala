package ai.lum.odinson

import org.scalatest._
import com.typesafe.config.Config
import ai.lum.common.ConfigUtils._
import ai.lum.odinson.compiler.QueryCompiler
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search._
import ai.lum.odinson.state.State
import ai.lum.odinson.utils.ConfigFactory
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
  val odinsonConfig = config[Config]("odinson")
  val documentIdField      = config[String]("odinson.index.documentIdField")
  val sentenceIdField      = config[String]("odinson.index.sentenceIdField")
  val sentenceLengthField  = config[String]("odinson.index.sentenceLengthField")
  val rawTokenField        = config[String]("odinson.index.rawTokenField")
  val defaultTokenField    = config[String]("odinson.compiler.defaultTokenField")

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
    ExtractorEngine.fromDirectory(odinsonConfig, memWriter.directory)
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
    sent.add(new TextField(defaultTokenField, text, Store.NO))
    sent
  }

}
