package ai.lum.odinson.lucene.search

import java.util.{Collection, TreeMap, Map => JMap}

import ai.lum.odinson.state.State

import scala.collection.JavaConverters._
import org.apache.lucene.index._
import org.apache.lucene.search._

/**
 * In some sense, the Query class is where it all begins. Without a Query,
 * there would be nothing to score. Furthermore, the Query class is the
 * catalyst for the other scoring classes as it is often responsible for
 * creating them or coordinating the functionality between them.
 *
 * (copied from lucene documentation)
 */
abstract class OdinsonQuery extends Query {

  def canEqual(a: Any): Boolean = a.isInstanceOf[OdinsonQuery]

  override def equals(that: Any): Boolean = that match {
    case that: OdinsonQuery => that.canEqual(this) && this.hashCode == that.hashCode
    case _ => false
  }

  def getField(): String

  // Override this if you're interested in the state, like if you are a StateQuery or if you
  // encapsulate other queries that might be interested in the state.
  def setState(stateOpt: Option[State]): Unit = ()
}

object OdinsonQuery {

  def getTermContexts(weights: OdinsonWeight*): JMap[Term, TermContext] = {
    val terms = new TreeMap[Term, TermContext]()
    weights.foreach(_.extractTermContexts(terms))
    terms
  }

  def getTermContexts(weights: Collection[OdinsonWeight]): JMap[Term, TermContext] = {
    val terms = new TreeMap[Term, TermContext]()
    for (w <- weights.iterator().asScala) {
      w.extractTermContexts(terms)
    }
    terms
  }

}
