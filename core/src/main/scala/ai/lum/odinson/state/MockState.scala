package ai.lum.odinson.state

import java.io.File

import ai.lum.odinson.state.OdinResultsIterator.emptyResultIterator


object MockState extends State {
  val emptyResultItemArray = new Array[ResultItem](0)

  def addResultItems(resultItems: Iterator[ResultItem]): Unit = ()

  def getDocIds(docBase: Int, label: String): Array[Int] = Array.emptyIntArray

  def getResultItems(docBase: Int, docId: Int, label: String): Array[ResultItem] = emptyResultItemArray

  override def getAllResultItems(): Iterator[ResultItem] = emptyResultIterator

  override def save(): Unit = ()

  override def saveTo(file: File): Unit = ()

  def clear(): Unit = ()

}
