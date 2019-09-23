package ai.lum.odinson.digraph

import java.io.IOException
import scala.collection.mutable
import org.apache.lucene.store.{ Directory, IOContext }

/** This vocabulary is meant for the labels of the edges of the dependency graph.
 *  This object maps a term_id (int) to a symbol (string).
 *  It is thread-safe. Note that id assignment is sensitive to the order in which terms
 *  are added to the vocabulary.
 */
class Vocabulary(
    private val idToTerm: mutable.ArrayBuffer[String],
    private val termToId: mutable.HashMap[String, Int]
) {

  def contains(id: Int): Boolean = idToTerm.isDefinedAt(id)

  def contains(term: String): Boolean = termToId.contains(term)

  def getId(term: String): Option[Int] = termToId.get(term)

  def getOrCreateId(term: String): Int = synchronized {
    termToId.getOrElseUpdate(term, {
      val id = idToTerm.length
      idToTerm += term
      termToId += Tuple2(term, id)
      id
    })
  }

  def getTerm(id: Int): Option[String] = {
    if (idToTerm isDefinedAt id) {
      Some(idToTerm(id))
    } else {
      None
    }
  }

  def terms = idToTerm.toVector

  def dump: String = {
    idToTerm.mkString(Vocabulary.sep)
  }

}

object Vocabulary {

  val sep = "\n"

  val FILE_NAME = "dependencies.txt"

  def empty: Vocabulary = {
    new Vocabulary(mutable.ArrayBuffer.empty, mutable.HashMap.empty)
  }

  def load(dump: String): Vocabulary = {
    val terms = dump.split(sep)
    val buffer = mutable.ArrayBuffer(terms: _*)
    val map = mutable.HashMap.empty[String, Int]
    map ++= terms.zipWithIndex
    new Vocabulary(buffer, map)
  }

  def fromDirectory(directory: Directory): Vocabulary = try {
    // FIXME: is this the correct instantiation of IOContext?
    val stream = directory.openInput(Vocabulary.FILE_NAME, new IOContext)
    Vocabulary.load(stream.readString())
  } catch {
    case e:IOException => Vocabulary.empty
  }

}
