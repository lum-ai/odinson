package ai.lum.odinson.state

import com.typesafe.config.Config

import scala.collection.mutable

class MemoryState extends State {

  type TokenInterval = (Int, Int)

  case class BaseIdLabel(docBase: Int, docId: Int, label: String)
  case class BaseLabel(docBase: Int, label: String)

  val baseIdLabelToTokenIntervals: mutable.Map[BaseIdLabel, mutable.SortedSet[TokenInterval]] = mutable.Map.empty
  val baseLabelToIds: mutable.Map[BaseLabel, mutable.SortedSet[Int]] = mutable.Map.empty

  def addMention(stateItem: ResultItem): Unit = {
    val baseIdLabel = BaseIdLabel(stateItem.segmentDocBase, stateItem.segmentDocId, stateItem.label)
    val tokenInterval = (stateItem.odinsonMatch.start, stateItem.odinsonMatch.end)
    val tokenIntervals = baseIdLabelToTokenIntervals.getOrElseUpdate(baseIdLabel, mutable.SortedSet.empty[TokenInterval])

    tokenIntervals.add(tokenInterval)

    val baseLabel = BaseLabel(stateItem.segmentDocBase, stateItem.label)
    val ids = baseLabelToIds.getOrElseUpdate(baseLabel, mutable.SortedSet.empty[Int])

    ids.add(stateItem.segmentDocId)
  }

  override def addResultItems(resultItems: Iterator[ResultItem]): Unit = {
    resultItems.foreach(addMention)
  }

  override def getDocIds(docBase: Int, label: String): Array[Int] = {
    val baseLabel = BaseLabel(docBase, label)
    val idsOpt = baseLabelToIds.get(baseLabel)
    val ids: Array[Int] =
      if (idsOpt.isDefined)
        idsOpt.get.toArray
      else
        Array.empty

    ids
  }

  override def getResultItems(docBase: Int, docId: Int, label: String): Array[ResultItem] = {
    Array.empty
//    val baseIdLabel = BaseIdLabel(docBase, docId, label)
//    val tokenIntervalsOpt = baseIdLabelToTokenIntervals.get(baseIdLabel)
//    val tokenIntervals: Array[TokenInterval] =
//      if (tokenIntervalsOpt.isDefined)
//        tokenIntervalsOpt.get.toArray
//      else
//        Array.empty
//
//    tokenIntervals
  }
}

class MemoryStateFactory extends StateFactory {

  override def usingState[T](function: State => T): T = {
    function(new MemoryState())
  }
}

object MemoryStateFactory {

  def apply(config: Config): MemoryStateFactory = {
    new MemoryStateFactory()
  }
}