package ai.lum.odinson.state

trait State {

  def addMentions(mentions: Iterator[(Int, Int, String, Int, Int)]): Unit

  def getDocIds(docBase: Int, label: String): Array[Int] // TODO: Return iterator

  def getMatches(docBase: Int, docId: Int, label: String): Array[(Int, Int)] // TODO: Return iterator

  // This may eventually go away, but it is needed for testing just now.
  def close(): Unit = ()
}
