package ai.lum.odinson.serialization

import java.util
import ai.lum.odinson.DataGatherer.VerboseLevels
import ai.lum.odinson.test.utils.OdinsonTest
import ai.lum.odinson.utils.exceptions.OdinsonException
import com.typesafe.config.ConfigValueFactory

class TestJsonSerialization extends OdinsonTest {

  val doc = getDocument("rainbows")
  val engine = mkExtractorEngine(doc)
  val storedFields = util.Arrays.asList("raw", "lemma", "tag")

  val verboseEngine = mkExtractorEngine(
    defaultConfig.withValue(
      "odinson.index.storedFields",
      ConfigValueFactory.fromIterable(storedFields)
    ),
    doc
  )

  val extractors = engine.compileRuleResource("/serialization.yml")

  // Without state
  val mentions = engine.extractNoState(extractors).toArray

  val jsonSerializer =
    new JsonSerializer(
      verbose = VerboseLevels.Minimal,
      indent = 4,
      dataGathererOpt = Some(engine.dataGatherer)
    )

  val displaySerializer =
    new JsonSerializer(
      verbose = VerboseLevels.Display,
      indent = 4,
      dataGathererOpt = Some(verboseEngine.dataGatherer)
    )

  val allSerializer =
    new JsonSerializer(
      verbose = VerboseLevels.All,
      indent = 4,
      dataGathererOpt = Some(verboseEngine.dataGatherer)
    )

  "JsonSerializer" should "handle NGramMentions" in {
    val m = getSingleMentionFromRule(mentions, "NGram")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle basic EventMatches" in {
    val m = getSingleMentionFromRule(mentions, "Event")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle EventMatches with arg quantifiers" in {
    val m = getSingleMentionFromRule(mentions, "Event-plus")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle EventMatches with arg ranges" in {
    val m = getSingleMentionFromRule(mentions, "Event-3")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle GraphTraversals" in {
    val m = getSingleMentionFromRule(mentions, "GraphTraversal")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Repetition" in {
    val m = getSingleMentionFromRule(mentions, "Repetition")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Repetition (lazy)" in {
    val m = getSingleMentionFromRule(mentions, "Repetition-lazy")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Optional" in {
    val m = getSingleMentionFromRule(mentions, "Optional")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Or" in {
    val m = getSingleMentionFromRule(mentions, "Or")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Named" in {
    val m = getSingleMentionFromRule(mentions, "Named")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  // With state
  val stateMentions = engine.extractMentions(extractors).toArray

  "JsonSerializer" should "handle NGramMentions with State" in {
    val m = getSingleMentionFromRule(stateMentions, "NGram")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle basic EventMatches with State" in {
    val m = getSingleMentionFromRule(stateMentions, "Event")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle EventMatches with arg quantifiers with State" in {
    val m = getSingleMentionFromRule(stateMentions, "Event-plus")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle EventMatches with arg ranges with State" in {
    val m = getSingleMentionFromRule(stateMentions, "Event-3")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle GraphTraversals with State" in {
    val m = getSingleMentionFromRule(stateMentions, "GraphTraversal")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Repetition with State" in {
    val m = getSingleMentionFromRule(stateMentions, "Repetition")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Repetition (lazy) with State" in {
    val m = getSingleMentionFromRule(stateMentions, "Repetition-lazy")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Optional with State" in {
    val m = getSingleMentionFromRule(stateMentions, "Optional")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Or with State" in {
    val m = getSingleMentionFromRule(stateMentions, "Or")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Named with State" in {
    val m = getSingleMentionFromRule(stateMentions, "Named")
    val json = jsonSerializer.asJsonValue(Array(m))
    val reconstituted = jsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "properly serialize and deserialized using json strings" in {
    val m = getSingleMentionFromRule(stateMentions, "Or")
    val jsonString = jsonSerializer.asJsonString(m)
    val deserialized = jsonSerializer.deserializeMention(jsonString)
    mentionsShouldBeEqual(m, deserialized) should be(true)

    val jsonPretty = jsonSerializer.asJsonPretty(m)
    val deserializedPretty = jsonSerializer.deserializeMention(jsonPretty)
    mentionsShouldBeEqual(m, deserializedPretty) should be(true)
  }

  it should "properly serialize and deserialize using json lines" in {
    val m1 = getSingleMentionFromRule(mentions, "Or")
    val m2 = getSingleMentionFromRule(mentions, "Named")
    val jsonLines = jsonSerializer.asJsonLines(Seq(m1, m2))
    val deserialized = jsonSerializer.deserializeJsonLines(jsonLines)
    deserialized should have length (2)
    mentionsShouldBeEqual(m1, deserialized(0)) should be(true)
    mentionsShouldBeEqual(m2, deserialized(1)) should be(true)
  }

  "JsonSerializer with verbose=display" should "include the display field and content" in {
    val nonevent = getSingleMentionFromRule(mentions, "MultipleWords")
    val json = displaySerializer.asJsonValue(nonevent)
    val detail = json("detail")
    val raw = detail("mention")("raw").arr.map(_.str)
    raw should contain inOrderOnly ("Rainbows", "shine", "bright")
    val docRaw = detail("document")("raw").arr.map(_.str)
    docRaw.mkString(" ") shouldBe "Rainbows shine bright bright bright ."

    a[java.util.NoSuchElementException] should be thrownBy json("lemma")
  }

  "JsonSerializer with verbose=all" should "include the all stored fields and content" in {
    val nonevent = getSingleMentionFromRule(mentions, "MultipleWords")
    val json = allSerializer.asJsonValue(nonevent)
    val detail = json("detail")("mention")

    detail("raw").arr.map(_.str) should contain inOrderOnly ("Rainbows", "shine", "bright")
    detail("lemma").arr.map(_.str) should contain inOrderOnly ("rainbow", "shine", "bright")
    detail("tag").arr.map(_.str) should contain inOrderOnly ("NNS", "VBP", "JJ")

    a[java.util.NoSuchElementException] should be thrownBy json("watermelon")
  }

  it should "deserialize mentions populated with previous content" in {
    val nonevent = getSingleMentionFromRule(mentions, "MultipleWords")
    val json = allSerializer.asJsonValue(nonevent)
    val emptySerializer = new JsonSerializer() // doesn't point to anything
    val deserialized = emptySerializer.deserializeMention(json)
    deserialized.text should be("Rainbows shine bright")
    deserialized.documentFields("raw") shouldBe Array(
      "Rainbows",
      "shine",
      "bright",
      "bright",
      "bright",
      "."
    )
    deserialized.mentionFields("tag") should contain inOrderOnly ("NNS", "VBP", "JJ")
  }

}
