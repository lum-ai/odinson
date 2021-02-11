package ai.lum.odinson.state

import java.io.File

import ai.lum.common.ConfigUtils._
import ai.lum.odinson.Mention
import ai.lum.odinson.lucene.search.OdinsonIndexSearcher
import com.typesafe.config.Config

trait State {

  // todo: accessor method in extractor engine
  def addMentions(mentions: Iterator[Mention]): Unit

  // todo: deleteResultItems ?  What would be the semantics

  def getDocIds(
    docBase: Int,
    label: String
  ): Array[Int] // TODO: Return iterator

  def getMentions(
    docBase: Int,
    docId: Int,
    label: String
  ): Array[Mention] // TODO: Return iterator

  // Note: may not be thread-safe
  def getAllMentions(): Iterator[Mention]

  /** Writes json lines representation of the ResultItems.  State retains its contents.
    * // TODO: should these be Mentions
    * @param file
    */
  def dump(file: File): Unit = {
    val contents = getAllMentions()
    val jsonlines = ???
    ???
  }

  /** Loads json lines representation of the ResultItems, adds them to the current state.
    * @param file
    */
  def load(file: File): Unit = {
    val jsonLines = ???
    val mentions = ???
    addMentions(mentions)
  }

  /** Delete the contents of the state, but leave the state open and able to store new results.
    */
  def clear(): Unit

  /** End connection (if any) to the state, finalize gracefully.
    */
  def close(): Unit = ()

}

object State {

  def apply(config: Config, indexSearcher: OdinsonIndexSearcher): State = {
    val provider = config[String]("odinson.state.provider")
    val state = provider match {
      // The SQL state needs an IndexSearcher to get the docIds from the
      case "sql"    => SqlState(config, indexSearcher)
      case "file"   => FileState(config)
      case "memory" => MemoryState(config)
      case "mock"   => MockState
      case _        => throw new Exception(s"Unknown state provider: $provider")
    }

    state
  }

}
