package ai.lum.odinson.state

import java.io.File

import ai.lum.common.ConfigUtils._
import com.typesafe.config.Config

trait State {

  def addResultItems(resultItems: Iterator[ResultItem]): Unit

  def getDocIds(docBase: Int, label: String): Array[Int] // TODO: Return iterator

  def getResultItems(docBase: Int, docId: Int, label: String): Array[ResultItem] // TODO: Return iterator

  def getAllResultItems(): Iterator[ResultItem]

  /**
    * Writes json lines representation of the ResultItems.  State retains its contents.
    * // TODO: should these be Mentions
    * @param file
    */
  def dump(file: File): Unit = ???

  /**
    * Loads json lines representation of the ResultItems, adds them to the current state.
    * @param file
    */
  def load(file: File): Unit = ???

  /**
    * Delete the contents of the state, but leave the state open and able to store new results.
    */
  def clear(): Unit

  /**
    * End connection (if any) to the state, finalize gracefully.
    */
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