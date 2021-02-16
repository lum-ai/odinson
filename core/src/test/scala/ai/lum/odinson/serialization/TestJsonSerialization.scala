package ai.lum.odinson.serialization

import ai.lum.odinson.utils.TestUtils.OdinsonTest

class TestJsonSerialization extends OdinsonTest {

  val doc = getDocument("rainbows")
  val engine = mkExtractorEngine(doc)

  val extractors = engine.compileRuleResource("/serialization.yml")

  // Without state
  val mentions = engine.extractNoState(extractors).toArray

  "JsonSerializer" should "handle NGramMentions" in {
    val m = getSingleMentionFromRule(mentions, "NGram")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle basic EventMatches" in {
    val m = getSingleMentionFromRule(mentions, "Event")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle EventMatches with arg quantifiers" in {
    val m = getSingleMentionFromRule(mentions, "Event-plus")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle EventMatches with arg ranges" in {
    val m = getSingleMentionFromRule(mentions, "Event-3")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle GraphTraversals" in {
    val m = getSingleMentionFromRule(mentions, "GraphTraversal")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Repetition" in {
    val m = getSingleMentionFromRule(mentions, "Repetition")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Repetition (lazy)" in {
    val m = getSingleMentionFromRule(mentions, "Repetition-lazy")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Optional" in {
    val m = getSingleMentionFromRule(mentions, "Optional")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Or" in {
    val m = getSingleMentionFromRule(mentions, "Or")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Named" in {
    val m = getSingleMentionFromRule(mentions, "Named")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  // With state
  val stateMentions = engine.extractMentions(extractors).toArray

  "JsonSerializer" should "handle NGramMentions with State" in {
    val m = getSingleMentionFromRule(stateMentions, "NGram")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle basic EventMatches with State" in {
    val m = getSingleMentionFromRule(stateMentions, "Event")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle EventMatches with arg quantifiers with State" in {
    val m = getSingleMentionFromRule(stateMentions, "Event-plus")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle EventMatches with arg ranges with State" in {
    val m = getSingleMentionFromRule(stateMentions, "Event-3")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle GraphTraversals with State" in {
    val m = getSingleMentionFromRule(stateMentions, "GraphTraversal")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Repetition with State" in {
    val m = getSingleMentionFromRule(stateMentions, "Repetition")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Repetition (lazy) with State" in {
    val m = getSingleMentionFromRule(stateMentions, "Repetition-lazy")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Optional with State" in {
    val m = getSingleMentionFromRule(stateMentions, "Optional")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Or with State" in {
    val m = getSingleMentionFromRule(stateMentions, "Or")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "handle Named with State" in {
    val m = getSingleMentionFromRule(stateMentions, "Named")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length (1)

    mentionsShouldBeEqual(m, reconstituted.head) should be(true)
  }

  it should "properly serialize and deserialized using json strings" in {
    val m = getSingleMentionFromRule(stateMentions, "Or")
    val jsonString = JsonSerializer.asJsonString(m)
    val deserialized = JsonSerializer.deserializeMention(jsonString)
    mentionsShouldBeEqual(m, deserialized) should be(true)

    val jsonPretty = JsonSerializer.asJsonPretty(m)
    val deserializedPretty = JsonSerializer.deserializeMention(jsonPretty)
    mentionsShouldBeEqual(m, deserializedPretty) should be(true)
  }

  it should "properly serialize and deserialized using json lines" in {
    val m1 = getSingleMentionFromRule(mentions, "Or")
    val m2 = getSingleMentionFromRule(mentions, "Named")
    val jsonLines = JsonSerializer.asJsonLines(Seq(m1, m2))
    val deserialized = JsonSerializer.deserializeJsonLines(jsonLines)
    deserialized should have length (2)
    mentionsShouldBeEqual(m1, deserialized(0)) should be(true)
    mentionsShouldBeEqual(m2, deserialized(1)) should be(true)
  }

}
