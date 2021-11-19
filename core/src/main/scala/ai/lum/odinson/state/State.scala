package ai.lum.odinson.state

import ai.lum.common.ConfigUtils._
import ai.lum.odinson.Mention
import ai.lum.odinson.lucene.index.OdinsonIndex
import ai.lum.odinson.state.Taxonomy.loadTaxonomy
import com.typesafe.config.Config
import org.apache.lucene.store.Directory

import java.io.File

trait State {

  def addTaxonomy(taxonomy: Taxonomy): Unit = {
    throw new UnsupportedOperationException("This type of state does not support usage of a taxonomy")
  }

  // todo: accessor method in extractor engine
  def addMentions(mentions: Iterator[Mention]): Unit

  // todo: deleteResultItems ?  What would be the semantics

  def getDocIds(docBase: Int, label: String): Array[Int] // TODO: Return iterator

  def getMentions(docBase: Int, docId: Int, label: String): Array[Mention] // TODO: Return iterator

  // Note: may not be thread-safe
  def getAllMentions(): Iterator[Mention]

  /** Writes json lines representation of the ResultItems.  State retains its contents.
    * // TODO: should these be Mentions
    *
    * @param file
    */
  def dump(file: File): Unit = {
    val contents = getAllMentions()
    val jsonlines = ???
    ???
  }

  /** Loads json lines representation of the ResultItems, adds them to the current state.
    *
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

  def TAXONOMY_CONFIG_PATH = "odinson.state.taxonomy"

  def apply(config: Config, index: OdinsonIndex): State = {
    val provider = config.apply[String]("odinson.state.provider")

    val state = provider match {
      // The SQL state needs an IndexSearcher to get the docIds from the
      case "sql"    => SqlState(config, index, Some(index.directory))
      case "file"   => FileState(config)
      case "memory" => MemoryState(config)
      case "mock"   => MockState
      case _        => throw new Exception(s"Unknown state provider: $provider")
    }

    config.get[String](TAXONOMY_CONFIG_PATH).foreach { taxonomyPath =>
      val taxonomy = loadTaxonomy(taxonomyPath)
      state.addTaxonomy(taxonomy)
    }


    state
  }

}
