package ai.lum.odinson.state

trait State {
  def addMention(docBase: Int, docId: Int, label: String, startToken: Int, endToken: Int): Unit

  def addMentions(mentions: Iterator[(Int, Int, String, Int, Int)]): Unit = {
    mentions.foreach { mention =>
      addMention(mention._1, mention._2, mention._3, mention._4, mention._5)
    }
  }

  def getDocIds(docBase: Int, label: String): Array[Int]

  def getMatches(docBase: Int, docId: Int, label: String): Array[(Int, Int)]

  // It may be cleaper to delete everything than to create a new State.
  def delete(): Unit = ???
}
