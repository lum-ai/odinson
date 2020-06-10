package ai.lum.odinson.state

trait State {
  def addMention(docBase: Int, docId: Int, label: String, startToken: Int, endToken: Int): Unit

  def getDocIds(docBase: Int, label: String): Array[Int]

  def getMatches(docBase: Int, docId: Int, label: String): Array[(Int, Int)]
}
