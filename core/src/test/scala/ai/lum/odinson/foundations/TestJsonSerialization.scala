package ai.lum.odinson.foundations

import ai.lum.odinson.serialization.JsonSerializer
import ai.lum.odinson.{BaseSpec, Mention, OdinsonMatch}
import ai.lum.odinson._

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
      val bArgs = b.arguments(arg)
      val aArgs = a.arguments(arg)
      bArgs.foreach { mention =>
        aArgs.exists(m => mentionsAreEqual(mention, m)) should be (true)
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
    b foreach { bNC =>
      a.exists(aNC => ncEqual(aNC, bNC)) should be (true)
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
    b foreach { bArgMD =>
      a.exists(aArgMD => mdEqual(aArgMD, bArgMD)) should be (true)
    }
  }


  val doc = getDocument("rainbows")
  val engine = Utils.mkExtractorEngine(doc)

  val extractors = engine.compileRuleResource("/serialization.yml")

  // Without state
  val mentions = engine.extractNoState(extractors).toArray

  "JsonSerializer" should "handle NGramMentions" in {
    val m = getMentionFromRule(mentions, "NGram")
    val json = JsonSerializer.jsonify(Array(m))
    val reconstituted = JsonSerializer.deserialize(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle basic EventMatches" in {
    val m = getMentionFromRule(mentions, "Event")
    val json = JsonSerializer.jsonify(Array(m))
    val reconstituted = JsonSerializer.deserialize(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle EventMatches with arg quantifiers" in {
    val m = getMentionFromRule(mentions, "Event-plus")
    val json = JsonSerializer.jsonify(Array(m))
    val reconstituted = JsonSerializer.deserialize(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle EventMatches with arg ranges" in {
    val m = getMentionFromRule(mentions, "Event-3")
    val json = JsonSerializer.jsonify(Array(m))
    val reconstituted = JsonSerializer.deserialize(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle GraphTraversals" in {
    val m = getMentionFromRule(mentions, "GraphTraversal")
    val json = JsonSerializer.jsonify(Array(m))
    val reconstituted = JsonSerializer.deserialize(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle Repetition" in {
    val m = getMentionFromRule(mentions, "Repetition")
    val json = JsonSerializer.jsonify(Array(m))
    val reconstituted = JsonSerializer.deserialize(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle Repetition (lazy)" in {
    val m = getMentionFromRule(mentions, "Repetition-lazy")
    val json = JsonSerializer.jsonify(Array(m))
    val reconstituted = JsonSerializer.deserialize(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle Optional" in {
    val m = getMentionFromRule(mentions, "Optional")
    val json = JsonSerializer.jsonify(Array(m))
    val reconstituted = JsonSerializer.deserialize(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle Or" in {
    val m = getMentionFromRule(mentions, "Or")
    val json = JsonSerializer.jsonify(Array(m))
    val reconstituted = JsonSerializer.deserialize(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  it should "handle Named" in {
    val m = getMentionFromRule(mentions, "Named")
    val json = JsonSerializer.jsonify(Array(m))
    val reconstituted = JsonSerializer.deserialize(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

  // With state
  val stateMentions = engine.extractMentions(extractors).toArray

  it should "handle StateMentions" in {
    // Shouldn't matter which, all OdinsonMatches will be StateMatches
    val m = getMentionFromRule(stateMentions, "Or")
    val json = JsonSerializer.jsonify(Array(m))
    val reconstituted = JsonSerializer.deserialize(json)
    reconstituted should have length(1)

    mentionsAreEqual(m, reconstituted.head) should be (true)
  }

}
