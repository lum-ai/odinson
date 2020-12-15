package ai.lum.odinson.serialization

import ai.lum.odinson.{BaseSpec, Mention, OdinsonMatch, _}

class TestJsonSerialization extends BaseSpec {

  def getMentionFromRule(ms: Seq[Mention], rulename: String): Mention = {
    ms.filter(_.foundBy == rulename).head
  }

  def mentionsAreEqual(a: Mention, b: Mention): Boolean = {
    b.foundBy should equal(a.foundBy)
    b.label should equal(a.label)
    b.idGetter.getDocId should be (a.idGetter.getDocId)
    b.idGetter.getSentId should be (a.idGetter.getSentId)
    b.luceneDocId should be (a.luceneDocId)
    b.luceneSegmentDocId should be (a.luceneSegmentDocId)
    b.luceneSegmentDocBase should be (a.luceneSegmentDocBase)
    b.arguments.keySet should contain theSameElementsAs (a.arguments.keySet)
    for (arg <- b.arguments.keySet) {
      val bArgs = b.arguments(arg).sortBy(m => (m.odinsonMatch.start, m.odinsonMatch.end))
      val aArgs = a.arguments(arg).sortBy(m => (m.odinsonMatch.start, m.odinsonMatch.end))
      for (i <- aArgs.indices) {
        mentionsAreEqual(aArgs(i), bArgs(i))
      }
    }
    matchesAreEqual(a.odinsonMatch, b.odinsonMatch)
  }

  def matchesAreEqual(a: OdinsonMatch, b: OdinsonMatch): Boolean = {
    a match {
      case a: StateMatch =>
        b shouldBe an [StateMatch]
        b.start should equal(a.start)
        b.end should equal(a.end)
        namedCapturesAreEqual(a.namedCaptures, b.namedCaptures)

      case a: NGramMatch =>
        b shouldBe an [NGramMatch]
        b.start should equal(a.start)
        b.end should equal(a.end)

      case a: EventMatch =>
        b shouldBe an [EventMatch]
        matchesAreEqual(a.trigger, b.asInstanceOf[EventMatch].trigger)
        namedCapturesAreEqual(a.namedCaptures, b.namedCaptures)
        argMetaDataAreEqual(a.argumentMetadata, b.asInstanceOf[EventMatch].argumentMetadata)

      case a: GraphTraversalMatch =>
        b shouldBe an [GraphTraversalMatch]
        matchesAreEqual(a.srcMatch, b.asInstanceOf[GraphTraversalMatch].srcMatch)
        matchesAreEqual(a.dstMatch, b.asInstanceOf[GraphTraversalMatch].dstMatch)

      case a: ConcatMatch =>
        b shouldBe an [ConcatMatch]
        matchesAreEqual(a.subMatches, b.asInstanceOf[ConcatMatch].subMatches)

      case a: RepetitionMatch =>
        b shouldBe an [RepetitionMatch]
        matchesAreEqual(a.subMatches, b.asInstanceOf[RepetitionMatch].subMatches)
        a.isGreedy should be (b.asInstanceOf[RepetitionMatch].isGreedy)

      case a: OptionalMatch =>
        b shouldBe an [OptionalMatch]
        matchesAreEqual(a.subMatch, b.asInstanceOf[OptionalMatch].subMatch)
        a.isGreedy should be (b.asInstanceOf[OptionalMatch].isGreedy)

      case a: OrMatch =>
        b shouldBe an [OrMatch]
        matchesAreEqual(a.subMatch, b.asInstanceOf[OrMatch].subMatch)
        a.clauseID should be (b.asInstanceOf[OrMatch].clauseID)

      case a: NamedMatch =>
        b shouldBe an [NamedMatch]
        matchesAreEqual(a.subMatch, b.asInstanceOf[NamedMatch].subMatch)
        a.name should equal(b.asInstanceOf[NamedMatch].name)
        a.label should equal(b.asInstanceOf[NamedMatch].label)

      case _ => ???
    }
    // If it didn't find an error above we're good!
    true
  }

  def matchesAreEqual(a: Array[OdinsonMatch], b: Array[OdinsonMatch]): Unit = {
    val sortedA = a.sortBy(m => (m.start, m.end))
    val sortedB = b.sortBy(m => (m.start, m.end))
    for (i <- sortedA.indices) {
      matchesAreEqual(sortedA(i), sortedB(i)) should be (true)
    }
  }

  def namedCapturesAreEqual(a: Array[NamedCapture], b: Array[NamedCapture]): Unit = {
    def ncEqual(a1: NamedCapture, b1: NamedCapture): Boolean = {
      b1.label should be (a1.label)
      b1.name should be (a1.name)
      matchesAreEqual(a1.capturedMatch, b1.capturedMatch)
    }
    val sortedA = a.sortBy(nc => (nc.capturedMatch.start, nc.capturedMatch.end))
    val sortedB = b.sortBy(nc => (nc.capturedMatch.start, nc.capturedMatch.end))
    for (i <- sortedA.indices) {
      ncEqual(sortedA(i), sortedB(i))
    }
  }

  def argMetaDataAreEqual(a: Array[ArgumentMetadata], b: Array[ArgumentMetadata]): Unit = {
    def mdEqual(a1: ArgumentMetadata, b1: ArgumentMetadata): Boolean = {
      b1.min should be (a1.min)
      b1.max should be (a1.max)
      b1.name should be (a1.name)
      b1.promote should be (a1.promote)
      // If it didn't find an error above we're good!
      true
    }
    val sortedA = a.sortBy(amd => (amd.name, amd.min, amd.max))
    val sortedB = b.sortBy(amd => (amd.name, amd.min, amd.max))
    for (i <- sortedA.indices) {
      mdEqual(sortedA(i), sortedB(i))
    }
  }


  val doc = getDocument("rainbows")
  val engine = Utils.mkExtractorEngine(doc)

  val extractors = engine.compileRuleResource("/serialization.yml")

  // Without state
  val mentions = engine.extractNoState(extractors).toArray

  "JsonSerializer" should "handle NGramMentions" in {
    val m = getMentionFromRule(mentions, "NGram")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle basic EventMatches" in {
    val m = getMentionFromRule(mentions, "Event")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle EventMatches with arg quantifiers" in {
    val m = getMentionFromRule(mentions, "Event-plus")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle EventMatches with arg ranges" in {
    val m = getMentionFromRule(mentions, "Event-3")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle GraphTraversals" in {
    val m = getMentionFromRule(mentions, "GraphTraversal")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle Repetition" in {
    val m = getMentionFromRule(mentions, "Repetition")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle Repetition (lazy)" in {
    val m = getMentionFromRule(mentions, "Repetition-lazy")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle Optional" in {
    val m = getMentionFromRule(mentions, "Optional")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle Or" in {
    val m = getMentionFromRule(mentions, "Or")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle Named" in {
    val m = getMentionFromRule(mentions, "Named")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  // With state
  val stateMentions = engine.extractMentions(extractors).toArray

  "JsonSerializer" should "handle NGramMentions with State" in {
    val m = getMentionFromRule(stateMentions, "NGram")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle basic EventMatches with State" in {
    val m = getMentionFromRule(stateMentions, "Event")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle EventMatches with arg quantifiers with State" in {
    val m = getMentionFromRule(stateMentions, "Event-plus")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle EventMatches with arg ranges with State" in {
    val m = getMentionFromRule(stateMentions, "Event-3")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle GraphTraversals with State" in {
    val m = getMentionFromRule(stateMentions, "GraphTraversal")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle Repetition with State" in {
    val m = getMentionFromRule(stateMentions, "Repetition")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle Repetition (lazy) with State" in {
    val m = getMentionFromRule(stateMentions, "Repetition-lazy")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle Optional with State" in {
    val m = getMentionFromRule(stateMentions, "Optional")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle Or with State" in {
    val m = getMentionFromRule(stateMentions, "Or")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle Named with State" in {
    val m = getMentionFromRule(stateMentions, "Named")
    val json = JsonSerializer.asJsonValue(Array(m))
    val reconstituted = JsonSerializer.deserializeMentions(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "properly serialize and deserialized using json strings" in {
    val m = getMentionFromRule(stateMentions, "Or")
    val jsonString = JsonSerializer.asJsonString(m)
    val deserialized = JsonSerializer.deserializeMention(jsonString)
    mentionsAreEqual(m, deserialized) should be (true)

    val jsonPretty = JsonSerializer.asJsonPretty(m)
    val deserializedPretty = JsonSerializer.deserializeMention(jsonPretty)
    mentionsAreEqual(m, deserializedPretty) should be (true)
  }

  it should "properly serialize and deserialized using json lines" in {
    val m1 = getMentionFromRule(mentions, "Or")
    val m2 = getMentionFromRule(mentions, "Named")
    val jsonLines = JsonSerializer.asJsonLines(Seq(m1, m2))
    val deserialized = JsonSerializer.deserializeJsonLines(jsonLines)
    deserialized should have length (2)
    mentionsAreEqual(m1, deserialized(0)) should be (true)
    mentionsAreEqual(m2, deserialized(1)) should be (true)
  }

}
