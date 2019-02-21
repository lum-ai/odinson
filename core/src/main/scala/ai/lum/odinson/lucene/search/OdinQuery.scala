package ai.lum.odinson.lucene.search

import java.util.{ Map => JMap, TreeMap, Collection }
import scala.collection.JavaConverters._
import scala.util.hashing.MurmurHash3
import org.apache.lucene.index._
import org.apache.lucene.search._
import ai.lum.odinson.lucene.search._

/**
 * In some sense, the Query class is where it all begins. Without a Query,
 * there would be nothing to score. Furthermore, the Query class is the
 * catalyst for the other scoring classes as it is often responsible for
 * creating them or coordinating the functionality between them.
 *
 * (copied from lucene documentation)
 */
abstract class OdinQuery extends Query {

  def canEqual(a: Any): Boolean = a.isInstanceOf[OdinQuery]

  override def equals(that: Any): Boolean = that match {
    case that: OdinQuery => that.canEqual(this) && this.hashCode == that.hashCode
    case _ => false
  }

  /** helper to construct hashes in subclasses */
  protected def mkHash(objs: Any*): Int = {
    val seed = classHash()
    MurmurHash3.orderedHash(objs, seed)
  }

  def getField(): String

}

object OdinQuery {

  def getTermContexts(weights: OdinWeight*): JMap[Term, TermContext] = {
    val terms = new TreeMap[Term, TermContext]()
    weights.foreach(_.extractTermContexts(terms))
    terms
  }

  def getTermContexts(weights: Collection[OdinWeight]): JMap[Term, TermContext] = {
    val terms = new TreeMap[Term, TermContext]()
    for (w <- weights.iterator().asScala) {
      w.extractTermContexts(terms)
    }
    terms
  }

}
