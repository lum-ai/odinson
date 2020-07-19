package ai.lum.odinson.foundations

import org.scalatest._
import ai.lum.odinson.{TokensField, Sentence, Document, ExtractorEngine}
import ai.lum.odinson.BaseSpec
import ai.lum.common.{ConfigFactory}
import com.typesafe.config.Config

class TestQueryCompiler extends BaseSpec {
  def getExtractorEngine = {
    val config = ConfigFactory.load()
    val odinsonConfig = config.getConfig("odinson")
    val rawTokenField = config.getString("odinson.index.rawTokenField")
    // create test sentences
    val text = "Rain causes flood"
    val tokens = TokensField(rawTokenField, text.split(" "), store = true)
    val sentence = Sentence(tokens.tokens.length, Seq(tokens))
    val doc1 = Document("testdoc1", Nil, Seq(sentence))
    val doc2 = Document("testdoc2", Nil, Seq(sentence))
    // instantiate
    val ee = ExtractorEngine.inMemory(Seq(doc1, doc2))
    // return ExtractorEngine with 2 documents
    ee
  }

  "OdinsonQueryCompiler" should "compile beginning and end markers correctly" in {
    // get fixture
    val ee = getExtractorEngine
    val qc = ee.compiler
    // test start
    qc.mkQuery("<s>").toString shouldEqual ("DocStartQuery")
    // test end
    qc.mkQuery("</s>").toString shouldEqual ("DocEndQuery")
  }

  it should "compile positive and negative lookahead correctly" in {
    // get fixture
    val ee = getExtractorEngine
    val qc = ee.compiler
    // test negative lookahead
    qc.mkQuery("(?!i)")
      .toString shouldEqual ("NotQuery(AllNGramsQuery(0),Lookahead(Wrapped(norm:i)))")
    // test positive lookahead
    qc.mkQuery("(?=i)").toString shouldEqual ("Lookahead(Wrapped(norm:i))")
  }

  it should "compile concatenation and disjunctives correctly" in {
    // get fixture
    val ee = getExtractorEngine
    val qc = ee.compiler
    // test or
    qc.mkQuery("foo|bar")
      .toString shouldEqual ("OrQuery([Wrapped(norm:foo),Wrapped(norm:bar)])")
    // test concatenation
    qc.mkQuery("(a)(b)")
      .toString shouldEqual ("Concat([Wrapped(norm:a),Wrapped(norm:b)])")
  }
}
