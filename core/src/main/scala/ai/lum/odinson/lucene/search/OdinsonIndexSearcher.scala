package ai.lum.odinson.lucene.search

import java.util.Collection
import java.util.concurrent.ExecutorService
import scala.collection.JavaConverters._
import org.apache.lucene.index._
import org.apache.lucene.search._
import ai.lum.odinson.lucene._

class OdinsonIndexSearcher(
    context: IndexReaderContext,
    executor: ExecutorService
) extends IndexSearcher(context, executor) {

  def this(r: IndexReader, e: ExecutorService) = this(r.getContext(), e)
  def this(r: IndexReader) = this(r, null)

  def odinSearch(query: OdinQuery): OdinResults = {
    val n = readerContext.reader().maxDoc()
    odinSearch(null, query, n)
  }

  def odinSearch(query: OdinQuery, n: Int): OdinResults = odinSearch(null, query, n)

  def odinSearch(after: OdinsonScoreDoc, query: OdinQuery, numHits: Int): OdinResults = {
    val limit = math.max(1, readerContext.reader().maxDoc())
    require(
      after == null || after.doc < limit,
      s"after.doc exceeds the number of documents in the reader: after.doc=${after.doc} limit=${limit}"
    )
    val cappedNumHits = math.min(numHits, limit)
    val manager = new CollectorManager[OdinCollector, OdinResults] {
      def newCollector(): OdinCollector = OdinCollector.create(cappedNumHits, after)
      def reduce(collectors: Collection[OdinCollector]): OdinResults = {
        val results = collectors.iterator.asScala.map(_.odinResults).toArray
        OdinResults.merge(0, cappedNumHits, results, true)
      }
    }
    search(query, manager)
  }

}
