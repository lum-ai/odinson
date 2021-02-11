package ai.lum.odinson.state

import java.io.File
import scala.collection.mutable
import ai.lum.common.ConfigUtils._
import ai.lum.odinson._
import ai.lum.odinson.Mention
import ai.lum.odinson.StateMatch
import com.typesafe.config.Config

// This version of MemoryState differs from most versions of State in that it does not need to
// serialize the OdinsonMatches and then deserialize them as StateMatches.  This version keeps
// the matches just as they are.  This might cause behavior changes in clients.  Beware!
class MemoryState(val persistOnClose: Boolean, val outfile: Option[File] = None)
    extends State {
  import MemoryState._

  if (persistOnClose) require(outfile.isDefined)

  implicit val resultItemOrdering: MemoryState.MentionOrdering.type =
    MemoryState.MentionOrdering

  protected val baseIdLabelToMentions
    : mutable.Map[BaseIdLabel, mutable.SortedSet[Mention]] = mutable.Map.empty

  protected val baseLabelToIds: mutable.Map[BaseLabel, mutable.SortedSet[Int]] =
    mutable.Map.empty

  protected def addMention(mention: Mention): Unit = {
    val baseIdLabel = BaseIdLabel(
      mention.luceneSegmentDocBase,
      mention.luceneSegmentDocId,
      mention.label.getOrElse("")
    )
    val mentions = baseIdLabelToMentions.getOrElseUpdate(
      baseIdLabel,
      mutable.SortedSet.empty[Mention]
    )

    mentions.add(mention)

    val baseLabel =
      BaseLabel(mention.luceneSegmentDocBase, mention.label.getOrElse(""))
    val ids =
      baseLabelToIds.getOrElseUpdate(baseLabel, mutable.SortedSet.empty[Int])

    ids.add(mention.luceneSegmentDocId)
  }

  override def addMentions(mentions: Iterator[Mention]): Unit = {
    mentions.foreach(addMention)
  }

  override def getDocIds(docBase: Int, label: String): Array[Int] = {
    val baseLabel = BaseLabel(docBase, label)
    val idsOpt: Option[mutable.SortedSet[Int]] = baseLabelToIds.get(baseLabel)
    val ids: Array[Int] = idsOpt.map(_.toArray).getOrElse(Array.empty)

    ids
  }

  def getMention(
    docBase: Int,
    docId: Int,
    label: Option[String],
    odinsonMatch: OdinsonMatch
  ): Option[Mention] = {
    if (label.isEmpty) None
    else getMention(docBase, docId, label.get, odinsonMatch)
  }

  def getMention(
    docBase: Int,
    docId: Int,
    label: String,
    odinsonMatch: OdinsonMatch
  ): Option[Mention] = {
    val candidates = getMentions(docBase, docId, label)
    var i = 0
    while (i < candidates.length) {
      if (candidates(i).odinsonMatch == odinsonMatch) {
        return Some(candidates(i))
      }
      i += 1
    }
    None
  }

  override def getMentions(
    docBase: Int,
    docId: Int,
    label: String
  ): Array[Mention] = {
    val baseIdLabel = BaseIdLabel(docBase, docId, label)
    val mentionsOpt = baseIdLabelToMentions.get(baseIdLabel)
    mentionsOpt.map(_.toArray).getOrElse(Array.empty)
  }

  override def getAllMentions(): Iterator[Mention] = {
    baseIdLabelToMentions
      .toIterator
      .flatMap { case (_, mentionSet) => mentionSet.toIterator }
  }

  override def clear(): Unit = {
    baseIdLabelToMentions.clear()
    baseLabelToIds.clear()
  }

  override def close(): Unit = {
    if (persistOnClose) dump(outfile.get)
    clear()
  }

}

object MemoryState {
  case class BaseIdLabel(docBase: Int, docId: Int, label: String)
  case class BaseLabel(docBase: Int, label: String)

  def apply(config: Config): MemoryState = {
    val persistOnClose = config[Boolean]("odinson.state.memory.persistOnClose")
    val saveTo = config.get[File]("odinson.state.memory.stateDir")
    new MemoryState(persistOnClose, saveTo)
  }

  // This original implementation is thought to create too many objects.
  // implicit val ordering: Ordering[ResultItem] = Ordering.by[ResultItem, (Int, Int)] { resultItem =>
  //   (resultItem.odinsonMatch.start, resultItem.odinsonMatch.end)
  // }

  // The compiler can't handle an implicit here.
  object MentionOrdering extends Ordering[Mention] {

    def compare(left: Mention, right: Mention): Int = {
      right match {
        case equal if left == right                             => 0
        case earlierDoc if left.luceneDocId < right.luceneDocId => -1
        case laterDoc if left.luceneDocId > right.luceneDocId   => 1
        case sameDocComesFirst
            if left.odinsonMatch.start < right.odinsonMatch.start => -1
        case sameDocComesAfter
            if left.odinsonMatch.start > right.odinsonMatch.start => 1
        case sameDocSameStart
            if left.odinsonMatch.start == right.odinsonMatch.start =>
          val leftStart =
            if (left.odinsonMatch.namedCaptures.isEmpty) left.odinsonMatch.start
            else left.odinsonMatch.namedCaptures.map(_.capturedMatch.start).min
          val rightStart =
            if (right.odinsonMatch.namedCaptures.isEmpty)
              right.odinsonMatch.start
            else right.odinsonMatch.namedCaptures.map(_.capturedMatch.start).min
          if (leftStart < rightStart) -1
          else if (rightStart < leftStart) 1
          else 0
        case _ => ???
      }
    }

  }

}
