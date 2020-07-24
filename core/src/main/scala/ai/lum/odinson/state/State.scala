package ai.lum.odinson.state

trait State {

  def addResultItems(resultItems: Iterator[ResultItem]): Unit

  def getDocIds(docBase: Int, label: String): Array[Int] // TODO: Return iterator

  def getResultItems(docBase: Int, docId: Int, label: String): Array[ResultItem] // TODO: Return iterator

  // This may eventually go away, but it is needed for testing just now.
//  def close(): Unit = ()
}
