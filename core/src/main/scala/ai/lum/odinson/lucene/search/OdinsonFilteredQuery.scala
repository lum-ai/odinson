package ai.lum.odinson.lucene.search

import java.util.{ Map => JMap, Set => JSet }
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.join._
import org.apache.lucene.search.spans._
import ai.lum.odinson.lucene.search.spans._

class OdinsonFilteredQuery(
  val query: OdinsonQuery,
  val filter: ToChildBlockJoinQuery,
) extends OdinsonQuery { self =>

  override def hashCode: Int = mkHash(query, filter)

  def toString(field: String): String = s"FiltereqQuery($query)"

  def getField(): String = query.getField()

  override def createWeight(
    searcher: IndexSearcher,
    needsScores: Boolean
  ): OdinsonWeight = ???

}
