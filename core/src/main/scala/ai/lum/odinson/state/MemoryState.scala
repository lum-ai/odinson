package ai.lum.odinson.state

import com.typesafe.config.Config

import scala.collection.mutable

class MemoryState extends State {

  type TokenInterval = (Int, Int)

  case class BaseIdLabel(docBase: Int, docId: Int, label: String)
  case class BaseLabel(docBase: Int, label: String)

  val baseIdLabelToTokenIntervals: mutable.Map[BaseIdLabel, mutable.SortedSet[TokenInterval]] = mutable.Map.empty
  val baseLabelToIds: mutable.Map[BaseLabel, mutable.SortedSet[Int]] = mutable.Map.empty

  def addMention(docBase: Int, docId: Int, label: String, startToken: Int, endToken: Int): Unit = {
    val baseIdLabel = BaseIdLabel(docBase, docId, label)
    val tokenInterval = (startToken, endToken)
    val tokenIntervals = baseIdLabelToTokenIntervals.getOrElseUpdate(baseIdLabel, mutable.SortedSet.empty[TokenInterval])

    tokenIntervals.add(tokenInterval)

    val baseLabel = BaseLabel(docBase, label)
    val ids = baseLabelToIds.getOrElseUpdate(baseLabel, mutable.SortedSet.empty[Int])

    ids.add(docId)
  }

  override def addMentions(mentions: Iterator[(Int, Int, String, Int, Int)]): Unit = {
    mentions.foreach { mention =>
      addMention(mention._1, mention._2, mention._3, mention._4, mention._5)
    }
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

  override def getMatches(docBase: Int, docId: Int, label: String): Array[(Int, Int)] = {
    val baseIdLabel = BaseIdLabel(docBase, docId, label)
    val tokenIntervalsOpt = baseIdLabelToTokenIntervals.get(baseIdLabel)
    val tokenIntervals: Array[TokenInterval] =
      if (tokenIntervalsOpt.isDefined)
        tokenIntervalsOpt.get.toArray
      else
        Array.empty

    tokenIntervals
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