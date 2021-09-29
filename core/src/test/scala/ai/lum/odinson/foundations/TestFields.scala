package ai.lum.odinson.foundations

import ai.lum.odinson.test.utils.OdinsonTest
import ai.lum.odinson.{ Document, Sentence, TokensField }
import com.typesafe.config.{ Config, ConfigValueFactory }

import scala.collection.JavaConverters.asJavaIterableConverter

class TestFields extends OdinsonTest {

  val json =
    """{"id":"56842e05-1628-447a-b440-6be78f669bf2","metadata":[],"sentences":[{"numTokens":5,"fields":[{"$type":"ai.lum.odinson.TokensField","name":"raw","tokens":["Becky","ate","gummy","bears","."],"store":true},{"$type":"ai.lum.odinson.TokensField","name":"word","tokens":["Becky","ate","gummy","bears","."]},{"$type":"ai.lum.odinson.TokensField","name":"tag","tokens":["NNP","VBD","JJ","NNS","."]},{"$type":"ai.lum.odinson.TokensField","name":"lemma","tokens":["becky","eat","gummy","bear","."]},{"$type":"ai.lum.odinson.TokensField","name":"entity","tokens":["I-PER","O","O","O","O"]},{"$type":"ai.lum.odinson.TokensField","name":"chunk","tokens":["B-NP","B-VP","B-NP","I-NP","O"]},{"$type":"ai.lum.odinson.GraphField","name":"dependencies","edges":[[1,0,"nsubj"],[1,3,"dobj"],[1,4,"punct"],[3,2,"amod"]],"roots":[1]}]}]}"""

  // extractor engine persists across tests (hacky way)
  val doc = getDocumentFromJson(json)
  val ee = mkExtractorEngine(doc)

  "Odinson" should "be case insensitive on the norm field (implicitly)" in {
    val q = ee.mkQuery("ATE")
    val results = ee.query(q)
    results.totalHits should equal(1)
    results.scoreDocs.head.matches should have size 1
  }

  it should "be case insensitive on the norm field (explicitly)" in {
    val q = ee.mkQuery("[norm=ATE]")
    val results = ee.query(q)
    results.totalHits should equal(1)
    results.scoreDocs.head.matches should have size 1
  }

  it should "match with correct case on the raw field" in {
    val q = ee.mkQuery("[raw=ate]")
    val results = ee.query(q)
    results.totalHits should equal(1)
    results.scoreDocs.head.matches should have size 1
  }

  it should "not match with wrong case on the raw field" in {
    val q = ee.mkQuery("[raw=ATE]")
    val results = ee.query(q)
    results.totalHits should equal(0)
  }

  behavior of "odinson"

  val words = TokensField("raw", "one two three four five six seven eight nine ten".split(" "))

  val fizzbuzz =
    TokensField("fizzbuzz", "one two fizz four buzz fizz seven eight fizz buzz".split(" "))

  val sentence = Sentence(words.tokens.length, Seq(words, fizzbuzz))
  val doc2 = Document("<TEST-ID>", Nil, Seq(sentence))

  val customConfig: Config = defaultConfig
    .withValue(
      "odinson.index.storedFields",
      ConfigValueFactory.fromAnyRef(Seq("raw", "fizzbuzz").asJava)
    )
    .withValue(
      "odinson.compiler.allTokenFields",
      ConfigValueFactory.fromAnyRef(Seq("raw", "fizzbuzz").asJava)
    )

  val ee2 = mkMemoryExtractorEngine(customConfig, doc2)

  it should "be able to index arbitrary fields" in {
    ee2.getTokensForSpan(0, "fizzbuzz", 2, 3) should contain only ("fizz")
  }

  it should "be able to compile patterns with arbitrary fields" in {
    noException shouldBe thrownBy(ee2.compiler.compile("[fizzbuzz = buzz]"))
  }

  it should "be able to execute patterns against arbitrary fields that are indexed" in {
    val rules =
      """
        |rules:
        |  - name: fizzbuzz_rule
        |    label: TestLabel
        |    type: basic
        |    pattern: |
        |      [fizzbuzz = buzz & raw = five]
        |""".stripMargin
    val extractors = ee2.compileRuleString(rules)
    val mentions = ee2.extractMentions(extractors).toList
    mentions should have size (1)
  }

  it should "throw an exception if pattern is written against arbitrary fields not indexed" in {
    a[java.lang.Exception] shouldBe thrownBy(ee.mkQuery("[other = buzz]"))
  }

}
