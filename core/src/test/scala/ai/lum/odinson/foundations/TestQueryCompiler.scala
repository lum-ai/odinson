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
    val doc3 = Document.fromJson(
      """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}"""
    )

    // instantiate
    val ee = ExtractorEngine.inMemory(Seq(doc1, doc2, doc3))
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
    // triple or
    qc.mkQuery("a|b|c")
      .toString shouldEqual ("OrQuery([Wrapped(norm:a),Wrapped(norm:b),Wrapped(norm:c)])")
    // test concatenation
    qc.mkQuery("(a)(b)")
      .toString shouldEqual ("Concat([Wrapped(norm:a),Wrapped(norm:b)])")
    // test triple concatenation
    qc.mkQuery("(a)(b)(c)")
      .toString shouldEqual ("Concat([Wrapped(norm:a),Wrapped(norm:b),Wrapped(norm:c)])")
  }

  it should "compile graph traversals correctly" in {
    // get fixture
    val ee = getExtractorEngine
    val qc = ee.compiler
    // basic test
    qc.compileEventQuery("""
      trigger = bar
      object: NP = >nsubj
      """).toString shouldEqual ("""Event(Wrapped(norm:bar) containing Wrapped(mask(outgoing:nsubj) as norm), [ArgumentQuery(object, Some(NP), 1, Some(1), ((Outgoing("nsubj"), StateQuery containing Wrapped(mask(incoming:nsubj) as norm))))], [])""")
  }
  
  it should "compile lazy repetitions correctly" in {
    // get fixture
    val ee = getExtractorEngine
    val qc = ee.compiler
    // test lazy quantifiers
    qc.compile("a+?").toString shouldEqual ("Repeat(Wrapped(norm:a), 1, 2147483647)")
    qc.compile("a*?").toString shouldEqual ("Optional(Repeat(Wrapped(norm:a), 1, 2147483647))")
    qc.compile("a??").toString shouldEqual ("Optional(Wrapped(norm:a))")
  }
  
  it should "compile greedy repetitions correctly" in {
    // get fixture
    val ee = getExtractorEngine
    val qc = ee.compiler
    // test lazy quantifiers
    // TODO is this right? lazy and greedy produce the same output
    qc.compile("a+").toString shouldEqual ("Repeat(Wrapped(norm:a), 1, 2147483647)")
    qc.compile("a*").toString shouldEqual ("Optional(Repeat(Wrapped(norm:a), 1, 2147483647))")
    qc.compile("a?").toString shouldEqual ("Optional(Wrapped(norm:a))")
  }
}
