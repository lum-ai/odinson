package ai.lum.odinson.lucene.search

import java.util.Collection
import java.util.concurrent.ExecutorService
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import org.apache.lucene.index._
import org.apache.lucene.search._
import ai.lum.odinson.lucene._
import ai.lum.odinson.utils.ExecutionContextExecutorServiceBridge

class OdinsonIndexSearcher(
    context: IndexReaderContext,
    executor: ExecutorService
) extends IndexSearcher(context, executor) {

  def this(r: IndexReader, e: ExecutorService) = this(r.getContext(), e)
  def this(r: IndexReader, e: ExecutionContext) = this(r.getContext(), ExecutionContextExecutorServiceBridge(e))
  def this(r: IndexReader) = this(r.getContext(), null)

  def odinSearch(query: OdinsonQuery): OdinResults = {
    val n = readerContext.reader().maxDoc()
    odinSearch(null, query, n)
  }

  def odinSearch(query: OdinsonQuery, n: Int): OdinResults = odinSearch(null, query, n)

  def odinSearch(after: OdinsonScoreDoc, query: OdinsonQuery, numHits: Int): OdinResults = {
    val limit = math.max(1, readerContext.reader().maxDoc())
    require(
      after == null || after.doc < limit,
      s"after.doc exceeds the number of documents in the reader: after.doc=${after.doc} limit=${limit}"
    )
    val cappedNumHits = math.min(numHits, limit)
    val manager = new CollectorManager[OdinsonCollector, OdinResults] {
      def newCollector() = new OdinsonCollector(cappedNumHits, after)
      def reduce(collectors: Collection[OdinsonCollector]): OdinResults = {
        val results = collectors.iterator.asScala.map(_.odinResults).toArray
        OdinResults.merge(0, cappedNumHits, results, true)
      }
    }
    search(query, manager)
  }

}
