package ai.lum.odinson.state.mock

import ai.lum.odinson.state.ResultItem
import ai.lum.odinson.state.State

object MockState extends State {
  val emptyResultItemArray = Array.empty[ResultItem]

  def addResultItems(resultItems: Iterator[ResultItem]): Unit = ()

  def getDocIds(docBase: Int, label: String): Array[Int] = Array.emptyIntArray

  def getResultItems(docBase: Int, docId: Int, label: String): Array[ResultItem] = emptyResultItemArray
}
