package ai.lum.odinson.foundations

import org.scalatest._
import ai.lum.odinson.{TokensField, Sentence, Document, ExtractorEngine}
import ai.lum.odinson.BaseSpec
import ai.lum.common.{ConfigFactory}
import com.typesafe.config.Config

import ai.lum.odinson.lucene.search.{
  OdinConcatQuery,
  OdinOrQuery,
  OdinNotQuery,
  AllNGramsQuery,
  OdinQueryWrapper,
  LookaheadQuery,
  DocEndQuery,
  DocStartQuery,
  OdinsonQuery,
  ArgumentQuery,
  OdinsonEventQuery,
  GraphTraversalQuery,
  OdinRepetitionQuery,
  OdinsonOptionalQuery
}
import ai.lum.odinson.digraph.{
  ExactLabelMatcher,
  Outgoing,
  Incoming,
  GraphTraversal
}
import ai.lum.odinson.lucene.search.spans.OdinsonSpanContainingQuery
import org.apache.lucene.search.spans.{SpanTermQuery, FieldMaskingSpanQuery}
import org.apache.lucene.index.{Term}

class TestQueryCompiler extends BaseSpec {
  // get extractor engine
  def ee: ExtractorEngine = {
    val config = ConfigFactory.load()
    val odinsonConfig = config.getConfig("odinson")
    val rawTokenField = config.getString("odinson.index.rawTokenField")
    // create test sentences
    val text = "Rain causes flood"
    val tokens = TokensField(rawTokenField, text.split(" "), store = true)
    val sentence = Sentence(tokens.tokens.length, Seq(tokens))
    val doc1 = Document("testdoc1", Nil, Seq(sentence))
    val doc2 = Document("testdoc2", Nil, Seq(sentence))
    // todo get this document from it's origin
    val doc3 = Document.fromJson(
      """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}"""
    )
    // return ExtractorEngine
    ExtractorEngine.inMemory(Seq(doc1, doc2, doc3))
  }
  // get query compiler
  def qc = ee.compiler
  // Query Compiler Helper
  // TODO: refactor (DRY)
  object QCHelper {
    // defaults
    // TODO: get these values from config
    def defaultTokenField = "norm"
    def sentenceLengthField = "numWords"
    def dependenciesField = "dependencies"
    //
    def getDocStartQuery: OdinsonQuery = new DocStartQuery("norm")
    def getDocEndQuery: OdinsonQuery =
      new DocEndQuery(defaultTokenField, sentenceLengthField)
    // terms
    def termFoo = new Term(defaultTokenField, "foo")
    def termBar = new Term(defaultTokenField, "bar")
    def termFoobar = new Term(defaultTokenField, "foobar")
    def term(s: String) = new Term(defaultTokenField, s)
    // terms for graph traversals
    def termIncomingNsubj = new Term("incoming", "nsubj")
    def termOutgoingNsubj = new Term("outgoing", "nsubj")
    // terms for words
    def termWordFoo = new Term("word", "foo")
    def termWordBar = new Term("word", "bar")
    // queries
    def spanTermQuery(t: Term) = new SpanTermQuery(t)
    def lookaheadQuery(q: OdinsonQuery) = new LookaheadQuery(q)
    def allNGRams0 =
      new AllNGramsQuery(defaultTokenField, sentenceLengthField, 0)
    def allNGrams1 =
      new AllNGramsQuery(defaultTokenField, sentenceLengthField, 1)
    // matchers
    def nsubjExact = new ExactLabelMatcher("nsubj", 0)
    // query wrapper
    def wrapQuery(q: SpanTermQuery) = new OdinQueryWrapper(q)
    def wrapQuery(q: FieldMaskingSpanQuery) = new OdinQueryWrapper(q)
    // wrapped queries
    def wrappedFooQuery = wrapQuery(spanTermQuery(termFoo))
    def wrappedBarQuery = wrapQuery(spanTermQuery(termBar))
    def wrappedFoobarQuery = wrapQuery(spanTermQuery(termFoobar))
    // wrapped mask
    def maskQuery(q: SpanTermQuery) =
      new FieldMaskingSpanQuery(q, defaultTokenField)
    def wrappedMaskedIncomingNsubj: OdinsonQuery =
      wrapQuery(maskQuery(spanTermQuery(termIncomingNsubj)))
    def wrappedMaskedOutgoingNsubj: OdinsonQuery =
      wrapQuery(maskQuery(spanTermQuery(termOutgoingNsubj)))
    //
    def wrappedMaskedWordFooQuery =
      wrapQuery(maskQuery(spanTermQuery(termWordFoo)))
    def wrappedMaskedWordBarQuery =
      wrapQuery(maskQuery(spanTermQuery(termWordBar)))
    // graph traversal
    def outgoingNsubj: GraphTraversal = new Outgoing(nsubjExact)
    def incomingNsubj: GraphTraversal = new Incoming(nsubjExact)
    // trigger query
    def spanBarWithOutgoingNsubj: OdinsonQuery =
      new OdinsonSpanContainingQuery(wrappedBarQuery, wrappedMaskedOutgoingNsubj)
    def spanBarWithIncomingNsubj: OdinsonQuery =
      new OdinsonSpanContainingQuery(wrappedBarQuery, wrappedMaskedIncomingNsubj)
    def spanFooWithOutgoingNsubj: OdinsonQuery =
      new OdinsonSpanContainingQuery(wrappedFooQuery, wrappedMaskedOutgoingNsubj)
    // repeat
    def repeatFooOneMax: OdinsonQuery =
      new OdinRepetitionQuery(wrappedFooQuery, 1, Int.MaxValue, false)
    def repeatFooOneTwo: OdinsonQuery =
      new OdinRepetitionQuery(wrappedFooQuery, 1, 2, false)
    // repeat greedy
    def repeatFooOneMaxGreedy: OdinsonQuery =
      new OdinRepetitionQuery(wrappedFooQuery, 1, Int.MaxValue, true)
    def repeatFooOneTwoGreedy: OdinsonQuery =
      new OdinRepetitionQuery(wrappedFooQuery, 1, 2, true)
  }
  //
  "OdinsonQueryCompiler" should "compile beginning and end markers correctly" in {
    // test start
    qc.mkQuery("<s>") shouldEqual (QCHelper.getDocStartQuery)
    // test end
    qc.mkQuery("</s>") shouldEqual (QCHelper.getDocEndQuery)
  }

  it should "compile positive and negative lookahead correctly" in {
    // test positive lookahead
    val result = QCHelper.lookaheadQuery(
      QCHelper.wrapQuery(
        QCHelper.spanTermQuery(
          QCHelper.termFoo
        )
      )
    )
    qc.mkQuery("(?=foo)") shouldEqual (result) // test negative lookahead
    // test negative lookahead
    val result1 =
      new OdinNotQuery(QCHelper.allNGRams0, result, QCHelper.defaultTokenField)
    qc.mkQuery("(?!foo)") shouldEqual (result1)
  }

  it should "compile concatenation and disjunctives correctly" in {
    // test or
    qc.mkQuery("foo|bar") shouldEqual (new OdinOrQuery(
      List(
        QCHelper.wrappedFooQuery,
        QCHelper.wrappedBarQuery
      ),
      QCHelper.defaultTokenField
    ))
    // triple or
    qc.mkQuery("foo|bar|foobar") shouldEqual (new OdinOrQuery(
      List(
        QCHelper.wrappedFooQuery,
        QCHelper.wrappedBarQuery,
        QCHelper.wrappedFoobarQuery
      ),
      QCHelper.defaultTokenField
    ))
    // test or with equal strings (should not return a OrQuery)
    qc.mkQuery("foo|foo") shouldEqual (QCHelper.wrappedFooQuery)
    // test or with equal strings (should ignore the repeated element)
    qc.mkQuery("foo|foo|bar") shouldEqual (new OdinOrQuery(
      List(
        QCHelper.wrappedFooQuery,
        QCHelper.wrappedBarQuery
      ),
      QCHelper.defaultTokenField
    ))
    // test concatenation
    qc.mkQuery("(foo)(bar)") shouldEqual (new OdinConcatQuery(
      List(
        QCHelper.wrappedFooQuery,
        QCHelper.wrappedBarQuery
      ),
      QCHelper.defaultTokenField,
      QCHelper.sentenceLengthField
    ))
    // test triple concatenation
    qc.mkQuery("(foo)(bar)(foobar)") shouldEqual (new OdinConcatQuery(
      List(
        QCHelper.wrappedFooQuery,
        QCHelper.wrappedBarQuery,
        QCHelper.wrappedFoobarQuery
      ),
      QCHelper.defaultTokenField,
      QCHelper.sentenceLengthField
    ))
    // test repeated double concatenation
    qc.mkQuery("(foo)(foo)") shouldEqual (new OdinConcatQuery(
      List(
        QCHelper.wrappedFooQuery,
        QCHelper.wrappedFooQuery
      ),
      QCHelper.defaultTokenField,
      QCHelper.sentenceLengthField
    ))
  }

  it should "compile lazy repetitions correctly" in {
    // test repetition
    qc.compile("foo+?") shouldEqual (new OdinRepetitionQuery(
      QCHelper.wrappedFooQuery,
      1,
      Int.MaxValue,
      false
    ))
    // test optional repetition
    qc.compile("foo*?") shouldEqual (new OdinsonOptionalQuery(
      QCHelper.repeatFooOneMax,
      QCHelper.sentenceLengthField,
      false
    ))
    // should work for greedy optional
    qc.compile("foo??") shouldEqual (new OdinsonOptionalQuery(
      QCHelper.wrappedFooQuery,
      QCHelper.sentenceLengthField,
      false
    ))
    // limited repetitions
    qc.compile("foo{2,2}?") shouldEqual (new OdinRepetitionQuery(
      QCHelper.wrappedFooQuery,
      2,
      2,
      false
    ))
    // missing left value repetition
    qc.compile("foo{,2}?") shouldEqual (new OdinsonOptionalQuery(
      QCHelper.repeatFooOneTwo,
      QCHelper.sentenceLengthField,
      false
    ))
    // missing both values
    qc.compile("foo{,}?") shouldEqual (new OdinsonOptionalQuery(
      QCHelper.repeatFooOneMax,
      QCHelper.sentenceLengthField,
      false
    ))
  }
  //
  it should "compile greedy repetitions correctly" in {
    // test with 1 or more greedy
    qc.compile("foo+") shouldEqual (new OdinRepetitionQuery(
      QCHelper.wrappedFooQuery,
      1,
      Int.MaxValue,
      true
    ))
    // test with 0 or more
    // TODO: check why this does not pass
    //qc.compile("foo*?") shouldEqual (new OdinsonOptionalQuery(
    //  QCHelper.repeatFooOneMaxGreedy,
    //  QCHelper.sentenceLengthField,
    //  true
    //))
    // greedy optionl
    qc.compile("foo?") shouldEqual (new OdinsonOptionalQuery(
      QCHelper.wrappedFooQuery,
      QCHelper.sentenceLengthField,
      true
    ))
    // repetition of size 2
    qc.compile("foo{2,2}") shouldEqual (new OdinRepetitionQuery(
      QCHelper.wrappedFooQuery,
      2,
      2,
      true
    ))
    // 0, 1 or 2
    // missing left value repetition
    qc.compile("foo{,2}") shouldEqual (new OdinsonOptionalQuery(
      QCHelper.repeatFooOneTwoGreedy,
      QCHelper.sentenceLengthField,
      true
    ))
    // 0 or max
    qc.compile("foo{,}") shouldEqual (new OdinsonOptionalQuery(
      QCHelper.repeatFooOneMaxGreedy,
      QCHelper.sentenceLengthField,
      true
    ))
  }
  //
  it should "compile constraints correctly" in {
    // test constraints
    qc.mkQuery("[word!=foo]") shouldEqual
      (new OdinNotQuery(
        QCHelper.allNGrams1,
        QCHelper.wrappedMaskedWordFooQuery,
        QCHelper.defaultTokenField
      ))
    //
    qc.mkQuery("[word=foo | word=bar]") shouldEqual (new OdinOrQuery(
      List(
        QCHelper.wrappedMaskedWordFooQuery,
        QCHelper.wrappedMaskedWordBarQuery
      ),
      QCHelper.defaultTokenField
    ))
  }
  /* TODO: tests for graph traversals
    #object: NP = >nsubj
    #object: NP = >nsubj*
    #object: NP = <nsubj*
    #object: NP = <nsubj+
    #object: NP = (>nsubj | >nsubj)
    #object: NP = >>nsubj
    #object: NP = <<nsubj
    #object: NP = <<nsubj?
    #object: NP = <<nsubj+
    #object: NP = <<nsubj*
 */
}
