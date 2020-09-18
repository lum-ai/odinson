package ai.lum.odinson.state

import java.io.File

import ai.lum.common.ConfigUtils._
import com.typesafe.config.Config

class FileState extends State {

  override def addResultItems(resultItems: Iterator[ResultItem]): Unit = ???

  override def getDocIds(docBase: Int, label: String): Array[Int] = ???

  override def getResultItems(docBase: Int, docId: Int, label: String): Array[ResultItem] = ???

  override def getAllResultItems(): Iterator[ResultItem] = ???

  override def clear(): Unit = ???

  override def close(): Unit = {
    ???
  }

}

object FileState {

  def apply(config: Config): FileState = {
    val saveOnClose = config[Boolean]("state.saveOnClose")
    val saveTo = config.get[File]("state.saveTo")
    new FileState()
  }

}
