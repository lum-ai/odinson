package ai.lum.odinson.state

class FileState extends State {

  override def addMention(docBase: Int, docId: Int, label: String, startToken: Int, endToken: Int): Unit = ???

  override def getDocIds(docBase: Int, label: String): Array[Int] = ???

  override def getMatches(docBase: Int, docId: Int, label: String): Array[(Int, Int)] = ???
}
