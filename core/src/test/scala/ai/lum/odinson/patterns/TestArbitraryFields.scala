package ai.lum.odinson.patterns

import ai.lum.common.ConfigFactory
import ai.lum.odinson.{ Document, Sentence, TokensField }
import ai.lum.odinson.utils.TestUtils.OdinsonTest
import ai.lum.odinson.utils.exceptions.OdinsonException
import com.typesafe.config.{ Config, ConfigValueFactory }

import scala.collection.JavaConverters.asJavaIterableConverter

class TestArbitraryFields extends OdinsonTest {

  behavior of "odinson"

  val words = TokensField("raw", "one two three four five six seven eight nine ten".split(" "))

  val fizzbuzz =
    TokensField("fizzbuzz", "one two fizz four buzz fizz seven eight fizz buzz".split(" "))

  val sentence = Sentence(words.tokens.length, Seq(words, fizzbuzz))
  val doc = Document("<TEST-ID>", Nil, Seq(sentence))

  val customConfig: Config = defaultConfig
    .withValue(
      "odinson.index.storedFields",
      ConfigValueFactory.fromAnyRef(Seq("raw", "fizzbuzz").asJava)
    )
    .withValue(
      "odinson.compiler.allTokenFields",
      ConfigValueFactory.fromAnyRef(Seq("raw", "fizzbuzz").asJava)
    )

  val ee = mkExtractorEngine(customConfig, doc)

  it should "be able to index arbitrary fields" in {
    ee.getTokensForSpan(0, "fizzbuzz", 2, 3) should contain only ("fizz")
  }

  it should "be able to compile patterns with arbitrary fields" in {
    noException shouldBe thrownBy(ee.compiler.compile("[fizzbuzz = buzz]"))
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
    val extractors = ee.compileRuleString(rules)
    val mentions = ee.extractMentions(extractors).toList
    mentions should have size (1)
  }

  // todo: should it warn? crash? etc.
  it should "throw an exception if pattern is written against arbitrary fields not indexed" in {
    an[OdinsonException] shouldBe thrownBy(ee.compiler.compile("[other = buzz]"))
  }

}
