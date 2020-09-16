package ai.lum.odinson.state

import ai.lum.odinson.state.OdinResultsIterator.emptyResultIterator
import com.typesafe.config.Config


object MockState extends State {
  val emptyResultItemArray = new Array[ResultItem](0)

  def addResultItems(resultItems: Iterator[ResultItem]): Unit = ()

  def getDocIds(docBase: Int, label: String): Array[Int] = Array.emptyIntArray

  def getResultItems(docBase: Int, docId: Int, label: String): Array[ResultItem] = emptyResultItemArray

  override def getAllResultItems(): Iterator[ResultItem] = emptyResultIterator
}


class MockStateFactory extends StateFactory {

  override def usingState[T](function: State => T): T = {
    function(MockState)
  }
}

object MockStateFactory {

  def apply(config: Config): MockStateFactory = {
    new MockStateFactory()
  }
}