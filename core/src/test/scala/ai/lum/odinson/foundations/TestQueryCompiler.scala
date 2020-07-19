package ai.lum.odinson.foundations

import org.scalatest._
import ai.lum.odinson.{BaseSpec, TokensField, Sentence, Document, ExtractorEngine}
import ai.lum.common.{ConfigFactory}
import com.typesafe.config.Config


class TestQueryCompiler extends BaseSpec {
  def getExtractorEngine = {
    val config = ConfigFactory.load()
    val odinsonConfig = config.getConfig("odinson")
    val rawTokenField = config.getString("odinson.index.rawTokenField")
    // create test sentences
    val text = "Rain causes flood"
    val tokens = TokensField(rawTokenField, text.split(" "), store=true)
    val sentence = Sentence(tokens.tokens.length, Seq(tokens))
    val doc1 = Document("testdoc1", Nil, Seq(sentence))
    val doc2 = Document("testdoc2", Nil, Seq(sentence))
    // instantiate
    val ee = ExtractorEngine.inMemory(Seq(doc1, doc2))
    // return ExtractorEngine with 2 documents
    ee
  }
}
