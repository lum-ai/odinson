package ai.lum.odinson.foundations

import org.scalatest._

import ai.lum.odinson.ExtractorEngine
import ai.lum.odinson.BaseSpec
import ai.lum.odinson.{TokensField, Sentence, Document}

import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.document.{Document => LuceneDocument, _}
import org.apache.lucene.document.Field.Store
import com.typesafe.config.Config
import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search._
import ai.lum.odinson.compiler.QueryCompiler
import ai.lum.odinson.state.State

class TestExtractorEngine extends BaseSpec {
  val config = ConfigFactory.load()
  val odinsonConfig = config[Config]("odinson")
  val rawTokenField = config[String]("odinson.index.rawTokenField")
  
  // create a test sentence
  val text = "Rain causes flood"
  val tokens = TokensField(rawTokenField, text.split(" "), store = true)
  val sentence = Sentence(tokens.tokens.length, Seq(tokens))
  val doc1 = Document("<TEST-ID1>", Nil, Seq(sentence))
  val doc2 = Document("<TEST-ID2>", Nil, Seq(sentence))
  
  val ee = ExtractorEngine.inMemory(Seq(doc1, doc2))
  // TODO: the compiler should be tested first
  // bc the rest of the stuff depends on it
  // TODO: make this a fixture
  // Check on code cov what to test
  //
  // test limited query
  // figure out how to create an OdinsonQuery
  // ee.query()
  
  "Odinson ExtractorEngine" should "run a simple query correctly" in {
    val q = ee.compiler.mkQuery("causes")
    val results = ee.query(q, 1)
    results.totalHits should equal (2)
  }

  // TODO: def getParentDoc(docId: String)
  // TODO: def compileRules(rules: String)
  // TODO: def extractMentions(extractors: Seq[Extractor], numSentences: Int)
  // TODO: def query(odinsonQuery: OdinsonQuery, n: Int, after: OdinsonScoreDoc)
  
  // TODO: def getArgument(mention: Mention, name: String)
  // TODO: getTokens(m: Mention): Array[String]
  // TODO: getTokens(scoreDoc: OdinsonScoreDoc, fieldName: String): Array[String]
  // TODO: ExtractorEngine.fromConfig
  // TODO: ExtractorEngine.fromConfig(path: String)
  // TODO: ExtractorEngine.fromConfig(config: Config)
}
