package ai.lum.odinson.state

import com.typesafe.config.Config

class FileState extends State {

  override def addMentions(mentions: Iterator[(Int, Int, String, Int, Int)]): Unit = ???

  override def getDocIds(docBase: Int, label: String): Array[Int] = ???

  override def getMatches(docBase: Int, docId: Int, label: String): Array[(Int, Int)] = ???
}

class FileStateFactory extends StateFactory {

  override def usingState[T](function: State => T): T = {
    function(new FileState())
  }
}

object FileStateFactory {

  def apply(config: Config): FileStateFactory = {
    new FileStateFactory()
  }
}
