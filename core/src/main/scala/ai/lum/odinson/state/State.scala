package ai.lum.odinson.state

import java.io.File

import ai.lum.common.ConfigUtils._
import com.typesafe.config.Config

trait State {

  val saveOnClose: Boolean = false
  val outfile: Option[File] = None
  // If you want to save on close, you need to specify a location
  if (saveOnClose) require(outfile.isDefined)

  def addResultItems(resultItems: Iterator[ResultItem]): Unit

  def getDocIds(docBase: Int, label: String): Array[Int] // TODO: Return iterator

  def getResultItems(docBase: Int, docId: Int, label: String): Array[ResultItem] // TODO: Return iterator

  def getAllResultItems(): Iterator[ResultItem]

  def save(): Unit = {
    require(outfile.isDefined)
    saveTo(outfile.get)
  }

  def saveTo(file: File): Unit

  def clear(): Unit

  def close(): Unit = ()
}

object State {

  def apply(config: Config): State = {
    val provider = config[String]("state.provider")
    val state = provider match {
      case "sql" => SqlState(config)
      case "file" => FileState(config)
      case "memory" => MemoryState(config)
      case "mock" => MockState
      case _ => throw new Exception(s"Unknown state provider: $provider")
    }

    state
  }
}