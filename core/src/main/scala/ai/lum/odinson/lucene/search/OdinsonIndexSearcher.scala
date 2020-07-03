package ai.lum.odinson.lucene.search

import java.util.Collection
import java.util.concurrent.ExecutorService

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import org.apache.lucene.index._
import org.apache.lucene.search._
import ai.lum.odinson.lucene._
import ai.lum.odinson.state.State
import ai.lum.odinson.utils.ExecutionContextExecutorServiceBridge
import ai.lum.odinson.utils.OdinResultsIterator

class OdinsonIndexSearcher(
    context: IndexReaderContext,
    executor: ExecutorService,
    computeTotalHits: Boolean,
) extends IndexSearcher(context, executor) {

  def this(r: IndexReader, e: ExecutorService, computeTotalHits: Boolean) = {
    this(r.getContext(), e, computeTotalHits)
  }

  def this(r: IndexReader, e: ExecutionContext, computeTotalHits: Boolean) = {
    this(r.getContext(), ExecutionContextExecutorServiceBridge(e), computeTotalHits)
  }

  def this(r: IndexReader, computeTotalHits: Boolean) = {
    this(r.getContext(), null, computeTotalHits)
  }

  def odinSearch(query: OdinsonQuery, state: State): OdinResults = {
    val n = readerContext.reader().maxDoc()
    odinSearch(query, state, n)
  }

  def odinSearch(query: OdinsonQuery, state: State, n: Int): OdinResults = {
    odinSearch(null, query, state, n)
  }

  def odinSearch(after: OdinsonScoreDoc, query: OdinsonQuery, state: State, numHits: Int): OdinResults = {
    odinSearch(after, query, None, state, numHits, false)
  }

  class StandardCollectorManager(after: OdinsonScoreDoc, cappedNumHits: Int, disableMatchSelector: Boolean) extends CollectorManager[OdinsonCollector, OdinResults] {

    def newCollector() = new OdinsonCollector(cappedNumHits, after, computeTotalHits, disableMatchSelector)

    def reduce(collectors: Collection[OdinsonCollector]): OdinResults = {
      val collectedResults = collectors.iterator.asScala.map(_.odinResults).toArray
      val mergedResults = OdinResults.merge(0, cappedNumHits, collectedResults, true)

      mergedResults
    }
  }

  def odinSearch(after: OdinsonScoreDoc, query: OdinsonQuery, label: Option[String], state: State, numHits: Int, disableMatchSelector: Boolean): OdinResults = {
    val limit = math.max(1, readerContext.reader().maxDoc())
    require(
      after == null || after.doc < limit,
      s"after.doc exceeds the number of documents in the reader: after.doc=${after.doc} limit=${limit}"
    )
    val cappedNumHits = math.min(numHits, limit)
    val manager = new StandardCollectorManager(after, cappedNumHits, disableMatchSelector)
    val odinResults: OdinResults = try {
      query.setState(Some(state))
      search(query, manager)
    }
    finally {
      query.setState(None)
    }
    val odinResultsIterator = OdinResultsIterator(odinResults, label)
    
    state.addMentions(odinResultsIterator)
    odinResults
  }
}
