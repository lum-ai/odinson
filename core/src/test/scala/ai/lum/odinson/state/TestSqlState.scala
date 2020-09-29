package ai.lum.odinson.state

import java.io.File

import ai.lum.odinson.{BaseSpec, DefaultMentionFactory, ExtractorEngine, NamedCapture, OdinsonMatch, StateMatch}
import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.OdinsonScoreDoc
import ai.lum.odinson.mention.LuceneMentionIterator
import ai.lum.odinson.mention.Mention
import ai.lum.odinson.mention.NullIdGetter
import com.typesafe.config.ConfigValueFactory

import scala.util.Random

class TestSqlState extends BaseSpec {
  val docBase = 42
  val docId = 13
  val docIndex = 212
  val resultLabel = "resultLabel"
  val resultName = "resultName"
  val factory = new DefaultMentionFactory()

  def newOdinsonMatch(): StateMatch = {
    val odinsonMatch_0_0 = StateMatch(0, 1, Array.empty)
    val namedCapture_0 = NamedCapture("name_0", Some("label_0"), odinsonMatch_0_0)

    val odinsonMatch_1_0 = StateMatch(1, 2, Array.empty)
    val namedCapture_1 = NamedCapture("name_1", Some("label_1"), odinsonMatch_1_0)

    val stateMatch = StateMatch(0, 2, Array(namedCapture_0, namedCapture_1))

    stateMatch
  }

  def newMention(docBase: Int = docBase, docId: Int = docId, docIndex: Int = docIndex, resultLabel: String = resultLabel,
      resultName: String = resultName): Mention = {
    val stateMatch = newOdinsonMatch()
    val resultLabelOpt =
        if (resultLabel.isEmpty) None
        else Some(resultLabel)
    val mention: Mention = factory.newMention(odinsonMatch = stateMatch, label = resultLabelOpt, luceneDocId = docIndex,
        luceneSegmentDocId = docId, luceneSegmentDocBase = docBase, idGetter = nullIdGetter, foundBy = resultName)

    mention
  }

  behavior of "Mention"

  it should "flatten" in {
    val resultItem = newMention()
    val idProvider = new IdProvider()
    val writeNodes = SqlResultItem.toWriteNodes(resultItem, idProvider)

    writeNodes.length should equal (3)

    val node0 = writeNodes(0)
    val node1 = writeNodes(1)
    val node2 = writeNodes(2)

    node0.id should equal (0)
    node0.parentId should equal(2)
    node0.name should equal ("name_0")
    node0.label should equal ("label_0")

    node1.id should equal (1)
    node1.parentId should equal(2)
    node1.name should be ("name_1")
    node1.label should equal ("label_1")

    node2.id should equal(2)
    node2.parentId should equal (-1)
    node2.name should equal ("resultName")
    node2.label should equal ("resultLabel")
  }

  def equals(left: NamedCapture, right: NamedCapture): Boolean = {
    left.name == right.name &&
        left.label == right.label &&
        equals(left.capturedMatch.asInstanceOf[StateMatch], right.capturedMatch.asInstanceOf[StateMatch])
  }

  def equals(left: StateMatch, right: StateMatch): Boolean = {
    left.start == right.start &&
        left.end == right.end &&
        left.namedCaptures.length == right.namedCaptures.length &&
        left.namedCaptures.indices.forall { index =>
          equals(left.namedCaptures(index), right.namedCaptures(index))
        }
  }

  def equals(left: Mention, right: Mention): Boolean = {
    left.luceneSegmentDocBase == right.luceneSegmentDocBase &&
        left.luceneSegmentDocId == right.luceneSegmentDocId &&
        left.luceneDocId == right.luceneDocId &&
        left.label == right.label &&
        left.foundBy == right.foundBy &&
        equals(left.odinsonMatch.asInstanceOf[StateMatch], right.odinsonMatch.asInstanceOf[StateMatch])
  }

  it should "compare properly" in {
    val m1 = newMention()
    val m2 = newMention()

    equals(m1, m2) should be (true)
  }

  it should "survive a round trip" in {
    val config = ExtractorEngine.defaultConfig
    val state = SqlState(config, null)
    val resultItem1 = newMention()
    val odinsonScoreDocs = Array(
      new OdinsonScoreDoc(docIndex, 0.0f, -1,
        Array(
          resultItem1.odinsonMatch
        ),
        docId, docBase)
    )
    val odinResults1 = new OdinResults(0, odinsonScoreDocs, 0.0f)
    val mentionsIterator = LuceneMentionIterator(Some(resultLabel), Some(resultName), odinResults1, factory, mruIdGetter)
    val resultItems2 = {
      state.addMentions(mentionsIterator)
      state.getMentions(docBase, docId, resultLabel)
    }

    resultItems2.length should be (1)
    val resultItem2 = resultItems2.head

    equals(resultItem1, resultItem2) should be (true)
  }

  val sizeOfString = 50

  def newRandomNamedCaptures(random: Random): Array[NamedCapture] = {
    val luck = random.nextInt(100)
    val size =
      if (luck < 50) 0
      else if (luck < 85) 1
      else if (luck < 95) 2
      else 3
    val namedCaptures = new Array[NamedCapture](size)

    0.until(size).foreach { index =>
      val name = random.nextString(sizeOfString)
      val label = random.nextString(sizeOfString)
      val capturedMatch = newRandomOdinsonMatch(random)

      namedCaptures(index) = NamedCapture(name, Some(label), capturedMatch)
    }
    namedCaptures
  }

  def newRandomOdinsonMatch(random: Random): OdinsonMatch = {
    val start = random.nextInt()
    val end = random.nextInt()
    val stateMatch = StateMatch(start, end, newRandomNamedCaptures(random))

    stateMatch
  }

  def newRandomOdinsonScoreDoc(random: Random, docId: Int, docBase: Int): OdinsonScoreDoc = {
    val docIndex = random.nextInt()
    val count = random.nextInt(20) + 1
    val odinsonMatches = 1.to(count).map { _ => newRandomOdinsonMatch(random) }.toArray
    val odinsonScoreDoc = new OdinsonScoreDoc(docIndex, 0.0f, -1,
        odinsonMatches, docId, docBase
    )

    odinsonScoreDoc
  }

  def newRandomOdinResults(random: Random, docId: Int, docBase: Int): OdinResults = {
    val count = random.nextInt(20) + 1
    val odinsonScoreDocs = 1.to(count).map { _ => newRandomOdinsonScoreDoc(random, docId, docBase) }.toArray
    val odinResults = new OdinResults(0, odinsonScoreDocs, 0.0f)

    odinResults
  }

  it should "work with one Mention at a time" in {
    val config = ExtractorEngine.defaultConfig
    val state = SqlState(config, null)
    val random = new Random(42)
    val docId = random.nextInt()
    val docBase = random.nextInt()
    val mentionFactory = new DefaultMentionFactory
    val idGetter = new NullIdGetter

    1.to(100).foreach { index => // Do this many tests.
      val odinResults = newRandomOdinResults(random, docId, docBase)
      // Convert to ResultItem so that can be compared later.
      val resultItems1 = odinResults.scoreDocs
          .flatMap { scoreDoc =>
              scoreDoc.matches.map { odinsonMatch =>
                mentionFactory.newMention(odinsonMatch, Some(resultLabel), scoreDoc.doc, scoreDoc.segmentDocId,
                  scoreDoc.segmentDocBase, idGetter, resultName)
              }
          }
      val mentionsIterator = LuceneMentionIterator(Some(resultLabel), Some(resultName), odinResults, factory, mruIdGetter)
      val resultItems2 = {
        state.addMentions(mentionsIterator)
        state.getMentions(docBase, docId, resultLabel)
      }

      resultItems1.length should be (resultItems2.length)
      resultItems1.zip(resultItems2).foreach { case (leftResultItem, rightResultItem) =>
        if (!equals(leftResultItem, rightResultItem))
          println(s"left: $leftResultItem != right: $rightResultItem")
        equals(leftResultItem, rightResultItem) should be (true)
      }
      state.clear()
    }
  }

  def newRandomDocBasesAndIdsAndLabels(random: Random): Array[(Int, Int, String)] = {
    val count = random.nextInt(5) + 4
    val docBasesAndIdsAndLabels = 1.to(count).map { _ =>
      val docId = random.nextInt()
      val docBase = random.nextInt()
      val label = random.nextString(sizeOfString)

      (docId, docBase, label)
    }.toSet.toArray

    docBasesAndIdsAndLabels
  }

  it should "work with multiple ResultItems at a time" in {
    val config = ExtractorEngine.defaultConfig
    val state = SqlState(config, null)
    val random = new Random(13)

    1.to(20).foreach { index => // Do this many tests.
      val docBasesAndIdsAndLabels = newRandomDocBasesAndIdsAndLabels(random)
      val odinResultses = docBasesAndIdsAndLabels.map { case (docBase, docId, label) => newRandomOdinResults(random, docId, docBase) }
      val resultItems1 = odinResultses.zip(docBasesAndIdsAndLabels).flatMap { case (odinResults, (_, _, label)) =>
        odinResults.scoreDocs.flatMap { scoreDoc =>
          scoreDoc.matches.map { odinsonMatch =>
            factory.newMention(odinsonMatch, Some(label), scoreDoc.doc, scoreDoc.segmentDocId, scoreDoc.segmentDocBase, nullIdGetter, resultName)
          }
        }
      }
      val resultItems2 = {
        odinResultses.zip(docBasesAndIdsAndLabels) foreach { case (odinResults, (_, _, label)) =>
          val mentionsIterator = LuceneMentionIterator(Some(label), Some(resultName), odinResults, factory, mruIdGetter)

          state.addMentions(mentionsIterator)
        }

        docBasesAndIdsAndLabels.flatMap { docBaseAndIdAndLabel: (Int, Int, String) =>
          val (docBase, docId, label) = docBaseAndIdAndLabel
          val mentions = state.getMentions(docBase, docId, label)

          mentions
        }
      }

      resultItems1.length should be (resultItems2.length)
      // Sort both of them.
      resultItems1.zip(resultItems2).foreach { case (leftResultItem, rightResultItem) =>
        if (!equals(leftResultItem, rightResultItem))
          println(s"left: $leftResultItem != right: $rightResultItem")
        equals(leftResultItem, rightResultItem) should be (true)
      }
    }
  }

  behavior of "persistent state"

  it should "persist" in {
    // Make sure it is sql in the first place
    val filename = "../test.sql"
    val file = new File(filename)
    val config = ExtractorEngine.defaultConfig
        .withValue("state.sql.persistOnClose", ConfigValueFactory.fromAnyRef(true))
        .withValue("state.sql.persistFile", ConfigValueFactory.fromAnyRef(filename))

    file.delete()
    file.exists should be (false)
    val state = SqlState(config, null)
    state.close()
    file.exists should be (true)
    file.delete()
  }
}