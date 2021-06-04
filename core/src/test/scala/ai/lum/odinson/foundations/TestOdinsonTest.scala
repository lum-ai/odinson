package ai.lum.odinson.foundations

import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.OdinsonScoreDoc
import ai.lum.odinson.state.{ MemoryState, SqlState }
import ai.lum.odinson.{
  ArgumentMetadata,
  EventMatch,
  Mention,
  NGramMatch,
  NamedCapture,
  NamedMatch,
  OdinsonMatch
}
import ai.lum.odinson.utils.TestUtils.OdinsonTest
import ai.lum.odinson.utils.exceptions.OdinsonException
import com.typesafe.config.ConfigValueFactory

import scala.collection.JavaConverters.asJavaIterableConverter

class TestOdinsonTest extends OdinsonTest {

  // John ate ramen with chopsticks and a spoon.
  // Daisy ate macaroni at her house.
  // Gus's pets include cats, dogs, parakeets, ponies, and unicorns.
  val ee = mkExtractorEngine("chopsticks-spoon")
  val dg = Some(ee.dataGatherer)

  behavior of "OdinsonTest"

  it should "convert matches to strings" in {
    //def mkStrings(results: OdinResults, engine: ExtractorEngine): Array[String] = {
    val odinsonMatches: Array[OdinsonMatch] =
      Array(new NGramMatch(2, 3), new NGramMatch(4, 5), new NGramMatch(7, 8))
    val scoreDoc = new OdinsonScoreDoc(doc = 0, score = 0.1f, matches = odinsonMatches)
    val results = new OdinResults(totalHits = 1, Array(scoreDoc))
    mkStrings(results, ee) should contain theSameElementsInOrderAs Seq(
      "ramen",
      "chopsticks",
      "spoon"
    )
  }

  it should "make ExtractorEngines with given attributes" in {
    val doc = getDocument("chopsticks-spoon")
    //def extractorEngineWithSpecificState(doc: Document, provider: String): ExtractorEngine = {
    val eeMem = extractorEngineWithSpecificState(doc, "memory")
    eeMem.state shouldBe a[MemoryState]

    val eeSQL = extractorEngineWithSpecificState(doc, "sql")
    eeSQL.state shouldBe a[SqlState]

    //def extractorEngineWithConfigValue(doc: Document, key: String, value: String): ExtractorEngine = {
    val eeFoobar = mkExtractorEngine(
      defaultConfig
        .withValue("odinson.displayField", ConfigValueFactory.fromAnyRef("foobar"))
        // The displayField is required to be in the storedFields
        .withValue(
          "odinson.index.storedFields",
          ConfigValueFactory.fromAnyRef(Seq("foobar").asJava)
        ),
      doc
    )
    eeFoobar.dataGatherer.displayField should equal("foobar")
  }

  it should "getMentionsFromRule" in {

    val m1 =
      new Mention(new NGramMatch(2, 3), Some("foobar"), 1, 1, 1, nullIdGetter, "FoobarRule", dg)
    val m2 =
      new Mention(new NGramMatch(4, 5), Some("foobar"), 1, 1, 1, nullIdGetter, "FoobarRule", dg)
    val mentions = Seq(m1, m2)

    // def getSingleMentionFromRule(ms: Seq[Mention], rulename: String): Mention = {
    val single = getSingleMentionFromRule(mentions, "FoobarRule")
    single should equal(m1)

    an[OdinsonException] should be thrownBy (getSingleMentionFromRule(mentions, "noMatch"))

    // def getMentionsFromRule(ms: Seq[Mention], rulename: String): Seq[Mention] = {
    val all = getMentionsFromRule(mentions, "FoobarRule")
    all should contain theSameElementsAs (Seq(m1, m2))

    getMentionsFromRule(mentions, "noMatch") shouldBe empty

  }

  it should "get mentions with given label" in {
    //def getMentionsWithLabel(ms: Seq[Mention], label: String): Seq[Mention] = {
    val m1 =
      new Mention(new NGramMatch(2, 3), Some("foobar"), 1, 1, 1, nullIdGetter, "FoobarRule", dg)
    val m2 =
      new Mention(new NGramMatch(4, 5), Some("foobar"), 1, 1, 1, nullIdGetter, "FoobarRule", dg)
    val mentions = Seq(m1, m2)

    getMentionsWithLabel(mentions, "foobar") should contain theSameElementsAs (Seq(m1, m2))
  }

  it should "get mentions with string value" in {
    //def getMentionsWithStringValue(ms: Seq[Mention], s: String, engine: ExtractorEngine): Seq[Mention] = {
    val m1 =
      new Mention(new NGramMatch(2, 3), Some("foobar"), 0, 0, 0, nullIdGetter, "FoobarRule", dg)
    val m2 =
      new Mention(new NGramMatch(4, 5), Some("foobar"), 1, 1, 1, nullIdGetter, "FoobarRule", dg)
    val mentions = Seq(m1, m2)

    getMentionsWithStringValue(mentions, "foobar", ee) shouldBe empty
    getMentionsWithStringValue(mentions, "ramen", ee) should contain theSameElementsAs (Seq(m1))
  }

  it should "determine if two mentions are equal" in {
    //def mentionsShouldBeEqual(a: Mention, b: Mention): Boolean = {
    val m1 =
      new Mention(new NGramMatch(2, 3), Some("foobar"), 0, 0, 0, nullIdGetter, "FoobarRule", dg)
    val m2 =
      new Mention(new NGramMatch(4, 5), Some("foobar"), 1, 1, 1, nullIdGetter, "FoobarRule", dg)

    mentionsShouldBeEqual(m1, m1) should be(true)
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy mentionsShouldBeEqual(
      m1,
      m2
    )

  }

  it should "determine if matches are equal" in {
    //def matchesShouldBeEqual(a: OdinsonMatch, b: OdinsonMatch): Boolean = {
    val eventMatch1 = new EventMatch(
      new NGramMatch(2, 3),
      Array(NamedCapture("name", Some("Label"), new NGramMatch(4, 5))),
      Array(ArgumentMetadata("argName", 1, Some(2), false))
    )
    // different namedcapture
    val eventMatch2 = new EventMatch(
      new NGramMatch(2, 3),
      Array(NamedCapture("othername", Some("Label"), new NGramMatch(4, 5))),
      Array(ArgumentMetadata("argName", 1, Some(2), false))
    )
    // diff argmetadata
    val eventMatch3 = new EventMatch(
      new NGramMatch(2, 3),
      Array(NamedCapture("name", Some("Label"), new NGramMatch(4, 5))),
      Array(ArgumentMetadata("argName", 1, Some(3), false))
    )
    // diff trigger
    val eventMatch4 = new EventMatch(
      new NGramMatch(3, 4),
      Array(NamedCapture("name", Some("Label"), new NGramMatch(4, 5))),
      Array(ArgumentMetadata("argName", 1, Some(2), false))
    )

    matchesShouldBeEqual(eventMatch1, eventMatch1) should be(true)

    an[org.scalatest.exceptions.TestFailedException] should be thrownBy matchesShouldBeEqual(
      eventMatch1,
      eventMatch2
    )
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy matchesShouldBeEqual(
      eventMatch1,
      eventMatch3
    )
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy matchesShouldBeEqual(
      eventMatch1,
      eventMatch4
    )

    // def matchesShouldBeEqual(a: Array[OdinsonMatch], b: Array[OdinsonMatch]): Unit = {
    val matches1: Array[OdinsonMatch] = Array(eventMatch1, eventMatch2, eventMatch3, eventMatch4)
    val matches1shuffled: Array[OdinsonMatch] =
      Array(eventMatch3, eventMatch2, eventMatch4, eventMatch1)
    val matches2: Array[OdinsonMatch] =
      Array(eventMatch3, new NGramMatch(1, 2), eventMatch4, eventMatch1)

    noException should be thrownBy matchesShouldBeEqual(matches1, matches1)
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy matchesShouldBeEqual(
      matches1,
      matches1shuffled
    )
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy matchesShouldBeEqual(
      matches1,
      matches2
    )
  }

  it should "determine if named captues are equal" in {
    //def namedCapturesAreEqual(a: Array[NamedCapture], b: Array[NamedCapture]): Unit = {
    val nc1 = NamedCapture("name", Some("Label"), new NGramMatch(4, 5))
    val nc2 = NamedCapture("othername", Some("Label"), new NGramMatch(4, 5))
    val nc3 = NamedCapture("name", Some("OtherLabel"), new NGramMatch(4, 5))
    val nc4 = NamedCapture("name", Some("Label"), new NGramMatch(1, 2))
    val nc5 = NamedCapture(
      "name",
      Some("Label"),
      new NamedMatch(new NGramMatch(1, 2), "namedmatch", Some("Label"))
    )

    val named = Array(nc1, nc2, nc3, nc4, nc5)

    noException should be thrownBy namedCapturesShouldBeEqual(named, named)
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy namedCapturesShouldBeEqual(
      Array(nc1),
      Array(nc2)
    )
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy namedCapturesShouldBeEqual(
      Array(nc1),
      Array(nc3)
    )
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy namedCapturesShouldBeEqual(
      Array(nc1),
      Array(nc4)
    )
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy namedCapturesShouldBeEqual(
      Array(nc1),
      Array(nc5)
    )

  }

  it should "determine if argument metadata are equal" in {
    //def argMetaDataAreEqual(a: Array[ArgumentMetadata], b: Array[ArgumentMetadata]): Unit = {
    val arg1 = ArgumentMetadata("argName", 1, Some(2), false)
    val arg2 = ArgumentMetadata("argName", 1, Some(2), true)
    val arg3 = ArgumentMetadata("differentArgName", 1, Some(2), false)
    val arg4 = ArgumentMetadata("argName", 2, Some(2), false)
    val arg5 = ArgumentMetadata("argName", 1, Some(3), false)

    noException should be thrownBy argMetaDataShouldBeEqual(
      Array(arg1, arg2, arg3, arg4, arg5),
      Array(arg1, arg2, arg3, arg4, arg5)
    )
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy argMetaDataShouldBeEqual(
      Array(arg1),
      Array(arg2)
    )
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy argMetaDataShouldBeEqual(
      Array(arg1),
      Array(arg3)
    )
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy argMetaDataShouldBeEqual(
      Array(arg1),
      Array(arg4)
    )
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy argMetaDataShouldBeEqual(
      Array(arg1),
      Array(arg5)
    )
  }

  it should "test mention contents" in {
    //def testMention(m: Mention, desiredMentionText: String, desiredArgs: Seq[Argument], engine: ExtractorEngine): Unit = {
    // non event without args
    val m1 =
      new Mention(new NGramMatch(2, 3), Some("foobar"), 0, 0, 0, nullIdGetter, "FoobarRule", dg)
    m1.populateFields()
    noException should be thrownBy testMention(m1, "ramen", Seq())

    // non event with args
    val m2 = new Mention(
      new NGramMatch(0, 1),
      Some("foobar"),
      0,
      0,
      0,
      nullIdGetter,
      "FoobarRule",
      Map("food" -> Array(m1)),
      dg
    )
    m2.populateFields()
    noException should be thrownBy testMention(m2, "John", Seq(Argument("food", "ramen")))

    // event, where the span of the mention is the trigger
    val eventMatch = new EventMatch(
      new NGramMatch(0, 1),
      Array(NamedCapture("food", Some("Label"), new NGramMatch(2, 3))),
      Array(ArgumentMetadata("food", 1, Some(2), false))
    )
    val m3 = new Mention(eventMatch, Some("foobar"), 0, 0, 0, nullIdGetter, "FoobarRule", dg)
    m3.populateFields()
    noException should be thrownBy testMention(m3, "John", Seq(Argument("food", "ramen")))

    an[org.scalatest.exceptions.TestFailedException] should be thrownBy testMention(
      m3,
      "Jacob",
      Seq(Argument("food", "ramen"))
    )
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy testMention(
      m3,
      "John",
      Seq(Argument("drink", "ramen"))
    )
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy testMention(
      m3,
      "John",
      Seq(Argument("food", "candy"))
    )
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy testMention(
      m3,
      "John",
      Seq(Argument("food", "ramen"), Argument("food", "candy"))
    )
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy testMention(
      m3,
      "John",
      Seq()
    )

    // event, with multiple args
    val eventMatch2 = new EventMatch(
      new NGramMatch(0, 1),
      Array(
        NamedCapture("food", Some("Label"), new NGramMatch(2, 3)),
        NamedCapture("food", Some("Label"), new NGramMatch(4, 5))
      ),
      Array(ArgumentMetadata("food", 1, Some(2), false))
    )
    val m4 = new Mention(eventMatch2, Some("foobar"), 0, 0, 0, nullIdGetter, "FoobarRule", dg)
    m4.populateFields()
    noException should be thrownBy testMention(
      m4,
      "John",
      Seq(Argument("food", "ramen"), Argument("food", "chopsticks"))
    )
    // diff order of args provided
    noException should be thrownBy testMention(
      m4,
      "John",
      Seq(Argument("food", "chopsticks"), Argument("food", "ramen"))
    )
    // repeated
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy testMention(
      m4,
      "John",
      Seq(Argument("food", "chopsticks"), Argument("food", "ramen"), Argument("food", "ramen"))
    )
  }

  it should "test event trigger" in {
    //def testEventTrigger(m: OdinsonMatch, start: Int, end: Int): Unit = {
    val eventMatch = new EventMatch(
      new NGramMatch(0, 1),
      Array(NamedCapture("food", Some("Label"), new NGramMatch(2, 3))),
      Array(ArgumentMetadata("food", 1, Some(2), false))
    )

    noException should be thrownBy testEventTrigger(eventMatch, 0, 1)
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy testEventTrigger(
      eventMatch,
      1,
      2
    )
    an[OdinsonException] should be thrownBy testEventTrigger(new NGramMatch(0, 1), 0, 1)

  }

  it should "test event trigger string" in {
    //def testEventTrigger(docID: Int, m: OdinsonMatch, engine: ExtractorEngine, text: String): Unit = {
    val eventMatch = new EventMatch(
      new NGramMatch(0, 1),
      Array(NamedCapture("food", Some("Label"), new NGramMatch(2, 3))),
      Array(ArgumentMetadata("food", 1, Some(2), false))
    )

    noException should be thrownBy testEventTrigger(0, eventMatch, ee, "John")
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy testEventTrigger(
      0,
      eventMatch,
      ee,
      "ramen"
    )
    an[OdinsonException] should be thrownBy testEventTrigger(0, new NGramMatch(0, 1), ee, "John")

  }

  it should "test arguments by offsets" in {
    //def testArguments(m: OdinsonMatch, desiredArgs: Seq[ArgumentOffsets]): Unit = {
    // event, with multiple args
    val eventMatch = new EventMatch(
      new NGramMatch(0, 1),
      Array(
        NamedCapture("food", Some("Label"), new NGramMatch(2, 3)),
        NamedCapture("food", Some("Label"), new NGramMatch(4, 5))
      ),
      Array(ArgumentMetadata("food", 1, Some(2), false))
    )

    noException should be thrownBy testArguments(
      eventMatch,
      Seq(ArgumentOffsets("food", 2, 3), ArgumentOffsets("food", 4, 5))
    )
    // diff order should be fine
    noException should be thrownBy testArguments(
      eventMatch,
      Seq(ArgumentOffsets("food", 4, 5), ArgumentOffsets("food", 2, 3))
    )

    // diff offsets
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy testArguments(
      eventMatch,
      Seq(ArgumentOffsets("food", 2, 3), ArgumentOffsets("food", 4, 6))
    )
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy testArguments(
      eventMatch,
      Seq(ArgumentOffsets("food", 1, 3), ArgumentOffsets("food", 4, 5))
    )
    // missing one
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy testArguments(
      eventMatch,
      Seq(ArgumentOffsets("food", 2, 3))
    )
    // missing one, other duplicated
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy testArguments(
      eventMatch,
      Seq(ArgumentOffsets("food", 2, 3), ArgumentOffsets("food", 2, 3))
    )
    // duplicated
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy testArguments(
      eventMatch,
      Seq(
        ArgumentOffsets("food", 2, 3),
        ArgumentOffsets("food", 2, 3),
        ArgumentOffsets("food", 4, 5)
      )
    )
  }

  it should "test arguments by content" in {
    //def testArguments(docId: Int, m: OdinsonMatch, desiredArgs: Seq[Argument], engine: ExtractorEngine): Unit = {
    // event, with multiple args
    val eventMatch = new EventMatch(
      new NGramMatch(0, 1),
      Array(
        NamedCapture("food", Some("Label"), new NGramMatch(2, 3)),
        NamedCapture("food", Some("Label"), new NGramMatch(4, 5))
      ),
      Array(ArgumentMetadata("food", 1, Some(2), false))
    )

    noException should be thrownBy testArguments(
      0,
      eventMatch,
      Seq(Argument("food", "ramen"), Argument("food", "chopsticks")),
      ee
    )
    // diff order should be fine
    noException should be thrownBy testArguments(
      0,
      eventMatch,
      Seq(Argument("food", "chopsticks"), Argument("food", "ramen")),
      ee
    )

    // diff string
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy testArguments(
      0,
      eventMatch,
      Seq(Argument("food", "ramen"), Argument("food", "spoon")),
      ee
    )
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy testArguments(
      0,
      eventMatch,
      Seq(Argument("food", "ramen"), Argument("tool", "chopsticks")),
      ee
    )
    // missing one
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy testArguments(
      0,
      eventMatch,
      Seq(Argument("food", "ramen")),
      ee
    )
    // missing one, other duplicated
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy testArguments(
      0,
      eventMatch,
      Seq(Argument("food", "ramen"), Argument("food", "ramen")),
      ee
    )
    // duplicated
    an[org.scalatest.exceptions.TestFailedException] should be thrownBy testArguments(
      0,
      eventMatch,
      Seq(Argument("food", "ramen"), Argument("food", "ramen"), Argument("food", "chopsticks")),
      ee
    )
  }

}
