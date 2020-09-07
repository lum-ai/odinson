package ai.lum.odinson.state

import com.typesafe.config.Config

import scala.collection.mutable

// This version of MemoryState differs from most versions of State in that it does not need to
// serialize the OdinsonMatches and then deserialize them as StateMatches.  This version keeps
// the matches just as they are.  This might cause behavior changes in clients.  Beware!
class MemoryState extends State {
  case class BaseIdLabel(docBase: Int, docId: Int, label: String)
  case class BaseLabel(docBase: Int, label: String)

  // This original implementation is thought to create too many objects.
  // implicit val ordering: Ordering[ResultItem] = Ordering.by[ResultItem, (Int, Int)] { resultItem =>
  //   (resultItem.odinsonMatch.start, resultItem.odinsonMatch.end)
  // }

  implicit object ResultItemOrdering extends Ordering[ResultItem] {
    def compare(left: ResultItem, right: ResultItem): Int = {
      val startSign = left.odinsonMatch.start - right.odinsonMatch.start
      if (startSign != 0) startSign
      else {
        val endSign = left.odinsonMatch.end - right.odinsonMatch.end
        endSign // and if these are tied...
      }
    }
  }

  protected val baseIdLabelToResultItems: mutable.Map[BaseIdLabel, mutable.SortedSet[ResultItem]] = mutable.Map.empty
  protected val baseLabelToIds: mutable.Map[BaseLabel, mutable.SortedSet[Int]] = mutable.Map.empty

  protected def addResultItem(resultItem: ResultItem): Unit = {
    val baseIdLabel = BaseIdLabel(resultItem.segmentDocBase, resultItem.segmentDocId, resultItem.label)
    val resultItems = baseIdLabelToResultItems.getOrElseUpdate(baseIdLabel, mutable.SortedSet.empty[ResultItem])

    resultItems.add(resultItem)

    val baseLabel = BaseLabel(resultItem.segmentDocBase, resultItem.label)
    val ids = baseLabelToIds.getOrElseUpdate(baseLabel, mutable.SortedSet.empty[Int])

    ids.add(resultItem.segmentDocId)
  }

  override def addResultItems(resultItems: Iterator[ResultItem]): Unit = {
    resultItems.foreach(addResultItem)
  }

  override def getDocIds(docBase: Int, label: String): Array[Int] = {
    val baseLabel = BaseLabel(docBase, label)
    val idsOpt: Option[mutable.SortedSet[Int]] = baseLabelToIds.get(baseLabel)
    val ids: Array[Int] = idsOpt.map(_.toArray).getOrElse(Array.empty)

    ids
  }

  override def getResultItems(docBase: Int, docId: Int, label: String): Array[ResultItem] = {
    val baseIdLabel = BaseIdLabel(docBase, docId, label)
    val resultItemsOpt = baseIdLabelToResultItems.get(baseIdLabel)
    val resultItems = resultItemsOpt.map(_.toArray).getOrElse(Array.empty)

    resultItems
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
