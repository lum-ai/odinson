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
  FullTraversalQuery,
  OdinsonEventQuery
}
import ai.lum.odinson.digraph.{ExactLabelMatcher, Outgoing, Incoming}
import ai.lum.odinson.lucene.search.spans.OdinsonSpanContainingQuery
import org.apache.lucene.search.spans.{SpanTermQuery, FieldMaskingSpanQuery}
import org.apache.lucene.index.{Term}

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

  // Query Compiler Helper
  // TODO: refactor (DRY)
  object QCHelper {
    // defaults
    // TODO: get these values from config
    def defaultTokenField = "norm"
    def sentenceLengthField = "numWords"
    def dependenciesField = "dependencies"
    // terms
    def termFoo = new Term("norm", "foo")
    def termBar = new Term("norm", "bar")
    def termFoobar = new Term("norm", "foobar")
    def term(s: String) = new Term("norm", s)
    def termIncomingNsubj = new Term("incoming", "nsubj")
    def termOutgoingNsubj = new Term("outgoing", "nsubj")
    // queries
    def spanTermQuery(t: Term) = new SpanTermQuery(t)
    def lookaheadQuery(q: OdinsonQuery) = new LookaheadQuery(q)
    def allNGRams0 = new AllNGramsQuery("norm", "numWords", 0)
    // matchers
    def nsubjExact = new ExactLabelMatcher("nsubj", 0)
    // query wraper
    def wrapQuery(q: SpanTermQuery) = new OdinQueryWrapper(q)
    def wrapQuery(q: FieldMaskingSpanQuery) = new OdinQueryWrapper(q)
    // wraped queries
    def wrapedFooQuery = wrapQuery(spanTermQuery(termFoo))
    def wrapedBarQuery = wrapQuery(spanTermQuery(termBar))
    def wrapedFoobarQuery = wrapQuery(spanTermQuery(termFoobar))
    // wraped mask
    def maskQuery(q: SpanTermQuery) = new FieldMaskingSpanQuery(q, "norm")
    def wrapedMaskedIncomingNsubj =
      wrapQuery(maskQuery(spanTermQuery(termIncomingNsubj)))
    def wrapedMaskedOutgoingNsubj =
      wrapQuery(maskQuery(spanTermQuery(termOutgoingNsubj)))
    // graph traversal
    def outgoingNsubj = new Outgoing(nsubjExact)
    // full traversal
    def fullTraversalOutgoingNsubj = FullTraversalQuery( List( (outgoingNsubj, wrapedMaskedIncomingNsubj) ).toList )
    //def fullTraversalOutgoingNsubj = FullTraversalQuery( List(outgoingNsubj, wrapedMaskedIncomingNsubj).toList)
    // argument query
    def argumentObjectOutgoingNsubjQuery = new ArgumentQuery("object", None, 1, Some(1), fullTraversalOutgoingNsubj)
    // event queries
    def eventTestQuery = 
      new OdinsonEventQuery(
        triggerBarWithIncomingNsubj,  
        List(argumentObjectOutgoingNsubjQuery), 
        List(), 
        "dependencies", 
        "numWords"
      )
    // trigger query
    def triggerBarWithIncomingNsubj = new OdinsonSpanContainingQuery(wrapedBarQuery, wrapedMaskedOutgoingNsubj)
  }

  "OdinsonQueryCompiler" should "compile beginning and end markers correctly" in {
    // get fixture
    val ee = getExtractorEngine
    val qc = ee.compiler
    // test start
    qc.mkQuery("<s>") shouldEqual (new DocStartQuery("norm")
      .asInstanceOf[OdinsonQuery])
    // test end
    qc.mkQuery("</s>") shouldEqual (new DocEndQuery("norm", "numWords")
      .asInstanceOf[OdinsonQuery])
  }

  it should "compile positive and negative lookahead correctly" in {
    // get fixture
    val ee = getExtractorEngine
    val qc = ee.compiler
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
    val result1 = new OdinNotQuery(QCHelper.allNGRams0, result, "norm")
    qc.mkQuery("(?!foo)") shouldEqual (result1)
  }

  it should "compile concatenation and disjunctives correctly" in {
    // get fixture
    val ee = getExtractorEngine
    val qc = ee.compiler
    // test or
    qc.mkQuery("foo|bar") shouldEqual (new OdinOrQuery(
      List(
        QCHelper.wrapedFooQuery,
        QCHelper.wrapedBarQuery
      ),
      "norm"
    ))
    // triple or
    qc.mkQuery("foo|bar|foobar") shouldEqual (new OdinOrQuery(
      List(
        QCHelper.wrapedFooQuery,
        QCHelper.wrapedBarQuery,
        QCHelper.wrapedFoobarQuery
      ),
      "norm"
    ))
    // test or with equal strings (should not return a OrQuery)
    qc.mkQuery("foo|foo") shouldEqual (QCHelper.wrapedFooQuery)
    // test or with equal strings (should ignore the repeated element)
    qc.mkQuery("foo|foo|bar") shouldEqual (new OdinOrQuery(
      List(
        QCHelper.wrapedFooQuery,
        QCHelper.wrapedBarQuery
      ),
      "norm"
    ))
    // test concatenation
    qc.mkQuery("(foo)(bar)") shouldEqual (new OdinConcatQuery(
      List(
        QCHelper.wrapedFooQuery,
        QCHelper.wrapedBarQuery
      ),
      "norm",
      "numWords"
    ))
    // test triple concatenation
    qc.mkQuery("(foo)(bar)(foobar)") shouldEqual (new OdinConcatQuery(
      List(
        QCHelper.wrapedFooQuery,
        QCHelper.wrapedBarQuery,
        QCHelper.wrapedFoobarQuery
      ),
      "norm",
      "numWords"
    ))
    // test repeated double concatenation
    qc.mkQuery("(foo)(foo)") shouldEqual (new OdinConcatQuery(
      List(
        QCHelper.wrapedFooQuery,
        QCHelper.wrapedFooQuery
      ),
      "norm",
      "numWords"
    ))
  }

  it should "compile graph traversals correctly" in {
    // get fixture
    val ee = getExtractorEngine
    val qc = ee.compiler
    //
    qc.compileEventQuery("""
      trigger = bar
      object = >nsubj
      """).toString shouldEqual ("""Event(Wrapped(norm:bar) containing Wrapped(mask(outgoing:nsubj) as norm), [ArgumentQuery(object, None, 1, Some(1), FullTraversalQuery(((Outgoing("nsubj"), Wrapped(mask(incoming:nsubj) as norm)))))], [])""")
    // 0 or more outgoings
    qc.compileEventQuery("""
      trigger = bar
      object: NP = >nsubj*
    """).toString shouldEqual ("""Event(Wrapped(norm:bar), [ArgumentQuery(object, Some(NP), 1, Some(1), FullTraversalQuery(((KleeneStar(Outgoing("nsubj")), StateQuery))))], [])""")
    // 0 or more incomings
    qc.compileEventQuery("""
      trigger = bar
      object: NP = <nsubj*
    """).toString shouldEqual ("""Event(Wrapped(norm:bar), [ArgumentQuery(object, Some(NP), 1, Some(1), FullTraversalQuery(((KleeneStar(Incoming("nsubj")), StateQuery))))], [])""")
    // two one or more incomings
    qc.compileEventQuery("""
      trigger = bar
      object: NP = <nsubj+
    """).toString shouldEqual ("""Event(Wrapped(norm:bar) containing Wrapped(mask(incoming:nsubj) as norm), [ArgumentQuery(object, Some(NP), 1, Some(1), FullTraversalQuery(((Concatenation(List(Incoming("nsubj"), KleeneStar(Incoming("nsubj")))), StateQuery))))], [])""")
    // two outgings
    qc.compileEventQuery("""
      trigger = bar
      object: NP = (>nsubj | >nsubj)
    """).toString shouldEqual ("""Event(Wrapped(norm:bar) containing Wrapped(mask(outgoing:nsubj) as norm), [ArgumentQuery(object, Some(NP), 1, Some(1), FullTraversalQuery(((Union(List(Outgoing("nsubj"), Outgoing("nsubj"))), StateQuery containing Wrapped(mask(incoming:nsubj) as norm)))))], [])""")
  }

  it should "compile graph traversals wildcards correctly" in {
    // get fixture
    val ee = getExtractorEngine
    val qc = ee.compiler
    //
    qc.compileEventQuery("""
      trigger = bar
      object: NP = >>nsubj
    """).toString shouldEqual ("""Event(Wrapped(norm:bar), [ArgumentQuery(object, Some(NP), 1, Some(1), FullTraversalQuery(((OutgoingWildcard, StateQuery containing Wrapped(norm:nsubj)))))], [])""")
    // <<
    qc.compileEventQuery("""
      trigger = bar
      object: NP = <<nsubj
    """).toString shouldEqual ("""Event(Wrapped(norm:bar), [ArgumentQuery(object, Some(NP), 1, Some(1), FullTraversalQuery(((IncomingWildcard, StateQuery containing Wrapped(norm:nsubj)))))], [])""")
    // << and ?
    qc.compileEventQuery("""
      trigger = bar
      object: NP = <<nsubj?
    """).toString shouldEqual ("""Event(Wrapped(norm:bar), [ArgumentQuery(object, Some(NP), 1, Some(1), FullTraversalQuery(((IncomingWildcard, StateQuery containing Optional(Wrapped(norm:nsubj))))))], [])""")
    // << and +
    qc.compileEventQuery("""
      trigger = bar
      object: NP = <<nsubj+
    """).toString shouldEqual ("""Event(Wrapped(norm:bar), [ArgumentQuery(object, Some(NP), 1, Some(1), FullTraversalQuery(((IncomingWildcard, StateQuery containing Repeat(Wrapped(norm:nsubj), 1, 2147483647)))))], [])""")
    // << and *
    qc.compileEventQuery("""
    trigger = bar
    object: NP = <<nsubj*
  """).toString shouldEqual ("""Event(Wrapped(norm:bar), [ArgumentQuery(object, Some(NP), 1, Some(1), FullTraversalQuery(((IncomingWildcard, StateQuery containing Optional(Repeat(Wrapped(norm:nsubj), 1, 2147483647))))))], [])""")
  }

  it should "compile lazy repetitions correctly" in {
    // get fixture
    val ee = getExtractorEngine
    val qc = ee.compiler
    // test lazy quantifiers
    qc.compile("a+?")
      .toString shouldEqual ("Repeat(Wrapped(norm:a), 1, 2147483647)")
    qc.compile("a*?")
      .toString shouldEqual ("Optional(Repeat(Wrapped(norm:a), 1, 2147483647))")
    qc.compile("a??").toString shouldEqual ("Optional(Wrapped(norm:a))")

    qc.compile("a{2,2}?").toString shouldEqual ("Repeat(Wrapped(norm:a), 2, 2)")
    qc.compile("a{,2}?")
      .toString shouldEqual ("Optional(Repeat(Wrapped(norm:a), 1, 2))")
    qc.compile("a{,}?")
      .toString shouldEqual ("Optional(Repeat(Wrapped(norm:a), 1, 2147483647))")
  }

  it should "compile greedy repetitions correctly" in {
    // get fixture
    val ee = getExtractorEngine
    val qc = ee.compiler
    // test lazy quantifiers
    // TODO is this right? lazy and greedy produce the same output
    qc.compile("a+")
      .toString shouldEqual ("Repeat(Wrapped(norm:a), 1, 2147483647)")
    qc.compile("a*")
      .toString shouldEqual ("Optional(Repeat(Wrapped(norm:a), 1, 2147483647))")
    qc.compile("a?").toString shouldEqual ("Optional(Wrapped(norm:a))")
    // same thing here
    qc.compile("a{2,2}").toString shouldEqual ("Repeat(Wrapped(norm:a), 2, 2)")
    qc.compile("a{,2}")
      .toString shouldEqual ("Optional(Repeat(Wrapped(norm:a), 1, 2))")
    qc.compile("a{,}")
      .toString shouldEqual ("Optional(Repeat(Wrapped(norm:a), 1, 2147483647))")
  }

  it should "compile constraints correctly" in {
    // get fixture
    val ee = getExtractorEngine
    val qc = ee.compiler
    // test constraints
    qc.compile("[word=a~]")
      .toString shouldEqual ("Wrapped(mask(SpanMultiTermQueryWrapper(word:a~2)) as norm)")
    qc.compile("[word!=a]")
      .toString shouldEqual ("NotQuery(AllNGramsQuery(1),Wrapped(mask(word:a) as norm))")
    qc.compile("[word=a | word=b]")
      .toString shouldEqual ("OrQuery([Wrapped(mask(word:a) as norm),Wrapped(mask(word:b) as norm)])")
    qc.compile("[word=a | word=b]")
      .toString shouldEqual ("OrQuery([Wrapped(mask(word:a) as norm),Wrapped(mask(word:b) as norm)])")
  }
}
