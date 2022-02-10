package ai.lum.odinson.lucene.index

import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.{ OdinsonCollector, OdinsonScoreDoc }
import org.apache.lucene.search.CollectorManager

import java.util.Collection
import scala.collection.JavaConverters._

class OdinsonCollectorManager(
  after: Int,
  cappedNumHits: Int,
  computeTotalHits: Boolean,
  disableMatchSelector: Boolean
) extends CollectorManager[OdinsonCollector, OdinResults] {

  def newCollector() = {
    new OdinsonCollector(cappedNumHits, after, computeTotalHits, disableMatchSelector)
  }

  def reduce(collectors: Collection[OdinsonCollector]): OdinResults = {
    val collectedResults = collectors.iterator.asScala.map(_.odinResults).toArray
    val mergedResults = OdinResults.merge(0, cappedNumHits, collectedResults, true)

    mergedResults
  }

}
