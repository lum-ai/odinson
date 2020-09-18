package ai.lum.odinson.state

import java.io.File

import ai.lum.common.ConfigUtils._
import com.typesafe.config.Config

class FileState(override val saveOnClose: Boolean, val saveTo: Option[File] = None) extends State {

  override def addResultItems(resultItems: Iterator[ResultItem]): Unit = ???

  override def getDocIds(docBase: Int, label: String): Array[Int] = ???

  override def getResultItems(docBase: Int, docId: Int, label: String): Array[ResultItem] = ???

  override def getAllResultItems(): Iterator[ResultItem] = ???

  override def saveTo(file: File): Unit = ???

  override def clear(): Unit = ???

  override def close(): Unit = {
    if (saveOnClose) save()
    ???
  }

}

object FileState {

  def apply(config: Config): FileState = {
    val saveOnClose = config[Boolean]("state.saveOnClose")
    val saveTo = config.get[File]("state.saveTo")
    new FileState(saveOnClose, saveTo)
  }

  def load(file: File): MemoryState = {
    ???
  }
}
