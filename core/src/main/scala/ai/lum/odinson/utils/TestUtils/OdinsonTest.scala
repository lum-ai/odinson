package ai.lum.odinson.utils.TestUtils

import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.odinson._
import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.OdinsonScoreDoc
import ai.lum.odinson.utils.MostRecentlyUsed
import ai.lum.odinson.utils.exceptions.OdinsonException
import com.typesafe.config.{ Config, ConfigValueFactory }
import org.scalatest._

import scala.collection.JavaConverters.asJavaIterableConverter

class OdinsonTest extends FlatSpec with Matchers {

  // Methods for initializing objects
  lazy val nullIdGetter = new NullIdGetter()
  lazy val mruIdGetter = MostRecentlyUsed[Int, LazyIdGetter](NullIdGetter.apply)

  val defaultConfig = ConfigFactory.load()
  val rawTokenField = defaultConfig.apply[String]("odinson.index.rawTokenField")

  def getDocumentFromJson(json: String): Document = Document.fromJson(json)
  def getDocument(id: String): Document = getDocumentFromJson(ExampleDocs.json(id))

  /** Get a list of strings, one for each match in the results.
    * @param results
    * @param engine
    * @return
    */
  def mkStrings(results: OdinResults, engine: ExtractorEngine): Array[String] = {
    for {
      scoreDoc <- results.scoreDocs
      doc = scoreDoc.doc
      m <- scoreDoc.matches
    } yield {
      engine.getStringForSpan(doc, m)
    }
  }

  // Methods for making extractor engines from docs

  /** Construct an ExtractorEngine with an in-memory index containing the provided Document,
    * and using the default config EXCEPT overriding the state to use the provided state
    * @param doc OdinsonDocument
    * @param provider the config value corresponding to the desired state
    * @return
    */
  def extractorEngineWithSpecificState(doc: Document, provider: String): ExtractorEngine = {
    extractorEngineWithConfigValue(doc, "odinson.state.provider", provider)
  }

  /** Constructs an ExtractorEngine with an in-memory index, and the default config, BUT with
    * the provided (key, value) override for the config (the key can be novel).
    * @param doc OdinsonDocument to include in the index
    * @param key config key to override or add
    * @param value config value to replace the default value or serve as value to new key
    * @return
    */
  def extractorEngineWithConfigValue(doc: Document, key: String, value: Any): ExtractorEngine = {
    val newConfig = defaultConfig.withValue(key, ConfigValueFactory.fromAnyRef(value))
    mkExtractorEngine(newConfig, doc)
  }

  def extractorEngineWithConfigValue(
    doc: Document,
    key: String,
    value: Seq[String]
  ): ExtractorEngine = {
    val newConfig = defaultConfig.withValue(key, ConfigValueFactory.fromAnyRef(value.asJava))
    mkExtractorEngine(newConfig, doc)
  }

  def extractorEngineWithSentenceStoredFields(
    doc: Document,
    fields: Seq[String]
  ): ExtractorEngine = {
    extractorEngineWithConfigValue(doc, "odinson.index.sentenceStoredFields", fields)
  }

  /** Constructs an `ai.lum.odinson.ExtractorEngine`` from a single-doc
    * using an in-memory index (`org.apache.lucene.store.RAMDirectory`)
    * @param docID the string key for the document from ai.lum.odinson.utils.TestUtils.ExampleDocs
    * @return
    */
  def mkExtractorEngine(docID: String): ExtractorEngine = {
    val doc = getDocument(docID)
    mkExtractorEngine(doc)
  }

  /** Constructs an [[ai.lum.odinson.ExtractorEngine]] from a single-doc
    * using an in-memory index ([[org.apache.lucene.store.RAMDirectory]]) and
    * the default config.
    * @param doc
    * @return ExtractorEngine
    */
  def mkExtractorEngine(doc: Document): ExtractorEngine = {
    ExtractorEngine.inMemory(doc)
  }

  /** Constructs an [[ai.lum.odinson.ExtractorEngine]] from a single-doc
    * using an in-memory index ([[org.apache.lucene.store.RAMDirectory]]) and
    * the provided config.
    * @param config
    * @param doc Odinson Document
    * @return ExtractorEngine
    */
  def mkExtractorEngine(config: Config, doc: Document): ExtractorEngine = {
    ExtractorEngine.inMemory(config, Seq(doc))
  }

  /** Converts the provided text to a minimal Document (with only a single field),
    * splitting the text into tokens on spaces.
    * Then constructs an [[ai.lum.odinson.ExtractorEngine]] from this doc
    * using an in-memory index ([[org.apache.lucene.store.RAMDirectory]]) and
    * the default config.
    * @param text the String text to be used
    * @return ExtractorEngine
    */
  def mkExtractorEngineFromText(text: String): ExtractorEngine = {
    val tokens = TokensField(rawTokenField, text.split("\\s+"))
    val sentence = Sentence(tokens.tokens.length, Seq(tokens))
    val document = Document("<TEST-ID>", Nil, Seq(sentence))
    mkExtractorEngine(document)
  }

  // Methods for checking query results (not Mentions)

  def numMatches(odinResults: OdinResults): Int = getAllMatches(odinResults).length

  def getAllMatches(odinResults: OdinResults): Array[(Int, OdinsonMatch)] = {
    for {
      scoreDoc <- odinResults.scoreDocs
      m <- scoreDoc.matches
    } yield (scoreDoc.doc, m)
  }

  def getOnlyMatch(odinResults: OdinResults): OdinsonMatch = {
    val matches = getAllMatches(odinResults)
    matches should have size (1)
    matches.head._2
  }

  def existsMatchWithSpan(odinResults: OdinResults, doc: Int, start: Int, end: Int): Boolean = {
    getAllMatches(odinResults)
      .collect {
        case (mDoc, mMatch) if (mDoc == doc && mMatch.start == start && mMatch.end == end) => mMatch
      }
      .nonEmpty
  }

  def existsMatchWithCapturedMatchSpan(
    odinResults: OdinResults,
    doc: Int,
    start: Int,
    end: Int
  ): Boolean = {
    val primary = getAllMatches(odinResults)
    val captured = for {
      (doc, m) <- primary
      nc <- m.namedCaptures
    } yield (doc, nc.capturedMatch)

    captured
      .collect {
        case (mDoc, mMatch) if (mDoc == doc && mMatch.start == start && mMatch.end == end) => mMatch
      }
      .nonEmpty
  }

  // Methods for checking mention/match equivalence

  def getSingleMentionFromRule(ms: Seq[Mention], rulename: String): Mention = {
    val opt = getMentionsFromRule(ms, rulename).headOption
    if (opt.isDefined) opt.get
    else throw new OdinsonException(s"No rules found with rulename: $rulename")
  }

  def getMentionsFromRule(ms: Seq[Mention], rulename: String): Seq[Mention] = {
    ms.filter(_.foundBy == rulename)
  }

  def getMentionsWithLabel(ms: Seq[Mention], label: String): Seq[Mention] = {
    ms.filter(_.label.getOrElse("") == label)
  }

  def getMentionsWithStringValue(
    ms: Seq[Mention],
    s: String,
    engine: ExtractorEngine
  ): Seq[Mention] = {
    ms.filter(m => engine.getStringForSpan(m.luceneDocId, m.odinsonMatch) == s)
  }

  /** Checks if the provided mentions are equivalent -- they have the same
    * document, span, internal OdinsonMatch, arguments, and label.
    * @param a
    * @param b
    * @return
    */
  def mentionsShouldBeEqual(a: Mention, b: Mention): Boolean = {
    b.foundBy should equal(a.foundBy)
    b.label should equal(a.label)
    b.idGetter.getDocId should be(a.idGetter.getDocId)
    b.idGetter.getSentId should be(a.idGetter.getSentId)
    b.luceneDocId should be(a.luceneDocId)
    b.luceneSegmentDocId should be(a.luceneSegmentDocId)
    b.luceneSegmentDocBase should be(a.luceneSegmentDocBase)
    b.arguments.keySet should contain theSameElementsAs (a.arguments.keySet)
    for (arg <- b.arguments.keySet) {
      val bArgs = b.arguments(arg).sortBy(m => (m.odinsonMatch.start, m.odinsonMatch.end))
      val aArgs = a.arguments(arg).sortBy(m => (m.odinsonMatch.start, m.odinsonMatch.end))
      for (i <- aArgs.indices) {
        mentionsShouldBeEqual(aArgs(i), bArgs(i))
      }
    }
    matchesShouldBeEqual(a.odinsonMatch, b.odinsonMatch)
  }

  /** Checks if the provided OdinsonMatches are equivalent -- they are the same type, and depending on the
    * type, they have the same internal attributes.
    * @param a
    * @param b
    * @return
    */
  def matchesShouldBeEqual(a: OdinsonMatch, b: OdinsonMatch): Boolean = {
    a match {
      case a: StateMatch =>
        b shouldBe an[StateMatch]
        b.start should equal(a.start)
        b.end should equal(a.end)
        namedCapturesShouldBeEqual(a.namedCaptures, b.namedCaptures)

      case a: NGramMatch =>
        b shouldBe an[NGramMatch]
        b.start should equal(a.start)
        b.end should equal(a.end)

      case a: EventMatch =>
        b shouldBe an[EventMatch]
        matchesShouldBeEqual(a.trigger, b.asInstanceOf[EventMatch].trigger)
        namedCapturesShouldBeEqual(a.namedCaptures, b.namedCaptures)
        argMetaDataShouldBeEqual(a.argumentMetadata, b.asInstanceOf[EventMatch].argumentMetadata)

      case a: GraphTraversalMatch =>
        b shouldBe an[GraphTraversalMatch]
        matchesShouldBeEqual(a.srcMatch, b.asInstanceOf[GraphTraversalMatch].srcMatch)
        matchesShouldBeEqual(a.dstMatch, b.asInstanceOf[GraphTraversalMatch].dstMatch)

      case a: ConcatMatch =>
        b shouldBe an[ConcatMatch]
        matchesShouldBeEqual(a.subMatches, b.asInstanceOf[ConcatMatch].subMatches)

      case a: RepetitionMatch =>
        b shouldBe an[RepetitionMatch]
        matchesShouldBeEqual(a.subMatches, b.asInstanceOf[RepetitionMatch].subMatches)
        a.isGreedy should be(b.asInstanceOf[RepetitionMatch].isGreedy)

      case a: OptionalMatch =>
        b shouldBe an[OptionalMatch]
        matchesShouldBeEqual(a.subMatch, b.asInstanceOf[OptionalMatch].subMatch)
        a.isGreedy should be(b.asInstanceOf[OptionalMatch].isGreedy)

      case a: OrMatch =>
        b shouldBe an[OrMatch]
        matchesShouldBeEqual(a.subMatch, b.asInstanceOf[OrMatch].subMatch)
        a.clauseID should be(b.asInstanceOf[OrMatch].clauseID)

      case a: NamedMatch =>
        b shouldBe an[NamedMatch]
        matchesShouldBeEqual(a.subMatch, b.asInstanceOf[NamedMatch].subMatch)
        a.name should equal(b.asInstanceOf[NamedMatch].name)
        a.label should equal(b.asInstanceOf[NamedMatch].label)

      case _ => ???
    }
    // If it didn't find an error above we're good!
    true
  }

  /** Tests that the provided Arrays of OdinsonMatches contain the same OdinsonMatches.
    * The Arrays need to be in the same order.
    * @param a
    * @param b
    */
  def matchesShouldBeEqual(a: Array[OdinsonMatch], b: Array[OdinsonMatch]): Unit = {
    for (i <- a.indices) {
      matchesShouldBeEqual(a(i), b(i)) should be(true)
    }
  }

  /** Tests that the provided Arrays of NamedCaptures contain the same NamedCaptures.
    * @param a
    * @param b
    */
  def namedCapturesShouldBeEqual(a: Array[NamedCapture], b: Array[NamedCapture]): Unit = {
    def ncEqual(a1: NamedCapture, b1: NamedCapture): Boolean = {
      b1.label should be(a1.label)
      b1.name should be(a1.name)
      matchesShouldBeEqual(a1.capturedMatch, b1.capturedMatch)
    }
    for (i <- a.indices) {
      ncEqual(a(i), b(i))
    }
  }

  /** Tests that the provided Arrays of ArgumentMetadata contain the same ArgumentMetadata.
    * @param a
    * @param b
    */
  def argMetaDataShouldBeEqual(a: Array[ArgumentMetadata], b: Array[ArgumentMetadata]): Unit = {
    def mdEqual(a1: ArgumentMetadata, b1: ArgumentMetadata): Boolean = {
      b1.min should be(a1.min)
      b1.max should be(a1.max)
      b1.name should be(a1.name)
      b1.promote should be(a1.promote)
      // If it didn't find an error above we're good!
      true
    }
    for (i <- a.indices) {
      mdEqual(a(i), b(i))
    }
  }

  // Methods for testing events and event attributes

  /** Tests that the provided OdinsonMatch has the provided desired text (potentially trigger text) and
    * arguments (by string value), and only those desired arguments.
    * This is specifically designed for testing extraction rules developed in downstream systems.
    * @param m
    * @param desiredMentionText the text that the Mention should consist of (for events, this is the trigger)
    * @param desiredArgs  the Arguments that should be found in the event
    */
  def testMention(
    m: Mention,
    desiredMentionText: String,
    desiredArgs: Seq[Argument]
  ): Unit = {
    // Check that the trigger is as desired
    m.text shouldEqual (desiredMentionText)

    // extract match arguments from the mathing objects
    val matchArgs = for {
      (argName, argMentions) <- m.arguments
      mention <- argMentions
    } yield Argument(argName, mention.text)

    // all desired args should be there, in the right number
    val groupedMatched = matchArgs.groupBy(_.name)
    val groupedDesired = desiredArgs.groupBy(_.name)

    groupedDesired.keySet should have size (groupedMatched.keySet.size)

    for ((desiredRole, desired) <- groupedDesired) {
      // there should be arg(s) of the desired label
      groupedMatched.keySet should contain(desiredRole)
      // should have the same number of arguments of that label
      val matchedForThisRole = groupedMatched(desiredRole).toSeq
      desired should have size matchedForThisRole.length
      for (d <- desired) {
        matchedForThisRole should contain(d)
      }
      // there shouldn't be any found arguments that we didn't want
      val unwantedArgs = groupedMatched.keySet.diff(groupedDesired.keySet)
      unwantedArgs shouldBe empty
    }
  }

  /** Tests that the OdinsonMatch (which is assumed to be an Event) has the provided
    * start and end.  Note that for an Event, the OdinsonMatch attribute is the trigger.
    * @param m
    * @param start token offset of the start of trigger (inclusive)
    * @param end  token offset of the end of the trigger (exclusive)
    */
  def testEventTrigger(m: OdinsonMatch, start: Int, end: Int): Unit = {
    val trigger = m match {
      case e: EventMatch => e.trigger
      case s: StateMatch => s
      case _             => throw new OdinsonException("Method only compatible with events")
    }
    trigger.start shouldEqual start
    trigger.end shouldEqual end
  }

  /** Tests that the OdinsonMatch (which is assumed to be an Event) has the provided
    * text.  Note that for an Event, the OdinsonMatch attribute is the trigger.
    * @param docID  the lucene doc id of the sentence containing the match
    * @param m      the OdinsonMatch
    * @param engine ExtractorEngine
    * @param text   the text value that the trigger should have
    */
  def testEventTrigger(docID: Int, m: OdinsonMatch, engine: ExtractorEngine, text: String): Unit = {
    val trigger = m match {
      case e: EventMatch => e.trigger
      case s: StateMatch => s
      case _             => throw new OdinsonException("Method only compatible with events")
    }
    engine.getStringForSpan(docID, trigger) shouldEqual (text)
  }

  /** Tests that the provided OdinsonMatch has the provided desired arguments, and only the desired
    * arguments.
    * @param m
    * @param desiredArgs
    */
  def testArguments(m: OdinsonMatch, desiredArgs: Seq[ArgumentOffsets]): Unit = {
    // extract match arguments from the mathing objects
    val matchArgs = for (nc <- m.namedCaptures)
      yield ArgumentOffsets(nc.name, nc.capturedMatch.start, nc.capturedMatch.end)
    // all desired args should be there, in the right number
    val groupedMatched = matchArgs.groupBy(_.name)
    val groupedDesired = desiredArgs.groupBy(_.name)
    //
    for ((desiredRole, desired) <- groupedDesired) {
      // there should be arg(s) of the desired label
      groupedMatched.keySet should contain(desiredRole)
      // should have the same number of arguments of that label
      val matchedForThisRole = groupedMatched(desiredRole)
      desired should have size matchedForThisRole.length
      for (d <- desired) {
        matchedForThisRole should contain(d)
      }
    }
    for ((foundRole, found) <- groupedMatched) {
      // there should be arg(s) of the desired label
      groupedDesired.keySet should contain(foundRole)
      // should have the same number of arguments of that label
      val desiredForThisRole = groupedDesired(foundRole)
      found should have size desiredForThisRole.length
      for (f <- found) {
        desiredForThisRole should contain(f)
      }
    }
    // there shouldn't be any found arguments that we didn't want
    val unwantedArgs = groupedMatched.keySet.diff(groupedDesired.keySet)
    unwantedArgs shouldBe empty
  }

  /** Tests that the provided OdinsonMatch has the provided desired arguments (by string value),
    * and only the desired arguments.
    * @param docId the lucene document id of the sentence containing the match
    * @param m
    * @param desiredArgs
    * @param engine ExtractorEngine
    */
  def testArguments(
    docId: Int,
    m: OdinsonMatch,
    desiredArgs: Seq[Argument],
    engine: ExtractorEngine
  ): Unit = {
    // extract match arguments from the mathing objects
    val matchArgs = for (nc <- m.namedCaptures)
      yield Argument(nc.name, engine.getStringForSpan(docId, nc.capturedMatch))
    // all desired args should be there, in the right number
    val groupedMatched = matchArgs.groupBy(_.name)
    val groupedDesired = desiredArgs.groupBy(_.name)

    for ((desiredRole, desired) <- groupedDesired) {
      // there should be arg(s) of the desired label
      groupedMatched.keySet should contain(desiredRole)
      // should have the same number of arguments of that label
      val matchedForThisRole = groupedMatched(desiredRole)
      desired should have size matchedForThisRole.length
      for (d <- desired) {
        matchedForThisRole should contain(d)
      }
    }

    for ((foundRole, found) <- groupedMatched) {
      // there should be arg(s) of the desired label
      groupedDesired.keySet should contain(foundRole)
      // should have the same number of arguments of that label
      val desiredForThisRole = groupedDesired(foundRole)
      found should have size desiredForThisRole.length
      for (f <- found) {
        desiredForThisRole should contain(f)
      }
    }

    // there shouldn't be any found arguments that we didn't want
    val unwantedArgs = groupedMatched.keySet.diff(groupedDesired.keySet)
    unwantedArgs shouldBe empty
  }

  /** Specifies a desired argument for testing, where the argument is defined by its name and offsets.
    * Note that the produced object has utility only with the testing utils.
    * @param name Name of the argument
    * @param start the token offset of the start (inclusive)
    * @param end the token offset of the end (exclusive)
    */
  case class ArgumentOffsets(name: String, start: Int, end: Int) {

    override def toString: String = {
      s"Argument(name=$name, start=$start, end=$end)"
    }

  }

  /** Specifies a desired Argument for testing, where the Argument is essentially defined by its
    * name and string value. Note that the produced object has utility only with the testing utils.
    * @param name Name of the argument
    * @param text the text the argument should contain
    */
  case class Argument(name: String, text: String) {

    override def toString: String = {
      s"Argument(name=$name, $text)"
    }

  }

}

// Used to satisfy requirements in testing, has no utility outside of the tests
class NullIdGetter() extends LazyIdGetter(null, 0) {
  override lazy val document = ???
  override lazy val docId: String = "NULL"
  override lazy val sentId: String = "NULL"
  override def getDocId: String = "NULL"
  override def getSentId: String = "NULL"
}

object NullIdGetter {
  // The x: Int is to fit the pattern of the mruIdGetter
  def apply(x: Int): NullIdGetter = new NullIdGetter()
}
