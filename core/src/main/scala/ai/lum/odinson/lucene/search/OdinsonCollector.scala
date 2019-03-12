package ai.lum.odinson.lucene.search

import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.util.PriorityQueue
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search._
import OdinsonCollector._



abstract class OdinsonCollector(
    protected val pq: PriorityQueue[OdinsonScoreDoc]
) extends Collector {

  def this(numHits: Int) = {
    this(new OdinsonHitsQueue(numHits, true))
    // HitQueue implements getSentinelObject to return a ScoreDoc, so we know
    // that at this point top() is already initialized.
    pqTop = pq.top()
  }

  protected var totalHits: Int = 0
  protected var pqTop: OdinsonScoreDoc = null

  override def needsScores(): Boolean = true

  protected def odinResultsSize(): Int = math.min(totalHits, pq.size())

  def odinResults(): OdinResults = odinResults(0, odinResultsSize())

  def odinResults(start: Int): OdinResults = odinResults(start, odinResultsSize())

  def odinResults(start: Int, howMany: Int): OdinResults = {
    val size = odinResultsSize()
    if (start < 0 || start >= size || howMany <= 0) {
      OdinResults.empty
    }
    // We know that start < pqsize, so just fix howMany.
    val fixedHowMany = math.min(size - start, howMany)
    val results = new Array[OdinsonScoreDoc](fixedHowMany)
    // pq's pop() returns the 'least' element in the queue, therefore need
    // to discard the first ones, until we reach the requested range.
    // Note that this loop will usually not be executed, since the common usage
    // should be that the caller asks for the last howMany results. However it's
    // needed here for completeness.
    var i = pq.size() - start - fixedHowMany
    while (i > 0) {
      pq.pop()
      i -= 1
    }
    // Get the requested results from pq.
    for (i <- fixedHowMany - 1 to 0 by -1) {
      results(i) = pq.pop()
    }
    new OdinResults(totalHits, results)
  }

  protected def populateResults(results: Array[OdinsonScoreDoc], howMany: Int): Unit = {
  }

  abstract class OdinLeafCollector extends LeafCollector {

    protected var scorer: OdinsonScorer = null

    override def setScorer(scorer: Scorer): Unit = scorer match {
      case s: OdinsonScorer => this.scorer = s
      case _ => sys.error("unsupported scorer")
    }

  }

}



object OdinsonCollector {

  def create(numHits: Int): OdinsonCollector = create(numHits, null)

  def create(numHits: Int, after: OdinsonScoreDoc): OdinsonCollector = {
    require(numHits > 0, "numHits must be > 0")
    if (after == null) {
      new SimpleOdinsonCollector(numHits)
    } else {
      new PagingOdinsonCollector(numHits, after)
    }
  }


  class OdinsonHitsQueue(
      size: Int,
      prePopulate: Boolean
  ) extends PriorityQueue[OdinsonScoreDoc](size, prePopulate) {

    override protected def getSentinelObject(): OdinsonScoreDoc = {
      // Always set the doc Id to MAX_VALUE so that it won't be favored by lessThan.
      new OdinsonScoreDoc(Int.MaxValue, Float.NegativeInfinity)
    }

    override protected def lessThan(hitA: OdinsonScoreDoc, hitB: OdinsonScoreDoc): Boolean = {
      if (hitA.score == hitB.score) {
        hitA.doc > hitB.doc
      } else {
        hitA.score < hitB.score
      }
    }

  }


  class SimpleOdinsonCollector(numHits: Int) extends OdinsonCollector(numHits) {

    def getLeafCollector(context: LeafReaderContext): LeafCollector = {
      val docBase = context.docBase
      new OdinLeafCollector {
        def collect(doc: Int): Unit = {
          val score = scorer.score()
          // This collector cannot handle these scores:
          assert(!score.isNegInfinity)
          assert(!score.isNaN)
          totalHits += 1
          if (score <= pqTop.score) {
            // Since docs are returned in-order (i.e., increasing doc Id), a document
            // with equal score to pqTop.score cannot compete since HitQueue favors
            // documents with lower doc Ids. Therefore reject those docs too.
            return
          }
          pqTop.doc = doc + docBase
          pqTop.score = score
          pqTop.matches = scorer.getMatches()
          pqTop.segmentDocId = doc
          pqTop.segmentDocBase = docBase
          pqTop = pq.updateTop()
        }
      }
    }

  }


  class PagingOdinsonCollector(
      numHits: Int,
      after: OdinsonScoreDoc
  ) extends OdinsonCollector(numHits) {

    private var collectedHits: Int = 0

    override def odinResultsSize(): Int = {
      if (collectedHits < pq.size()) collectedHits else pq.size()
    }

    def getLeafCollector(context: LeafReaderContext): LeafCollector = {
      val docBase = context.docBase
      val afterDoc = after.doc - context.docBase
      new OdinLeafCollector {
        def collect(doc: Int): Unit = {
          val score = scorer.score()
          // This collector cannot handle these scores:
          assert(!score.isNegInfinity)
          assert(!score.isNaN)
          totalHits += 1
          if (score > after.score || (score == after.score && doc <= afterDoc)) {
            // hit was collected on a previous page
            return
          }
          if (score <= pqTop.score) {
            // Since docs are returned in-order (i.e., increasing doc Id), a document
            // with equal score to pqTop.score cannot compete since HitQueue favors
            // documents with lower doc Ids. Therefore reject those docs too.
            return
          }
          collectedHits += 1
          pqTop.doc = doc + docBase
          pqTop.score = score
          pqTop.matches = scorer.getMatches()
          pqTop.segmentDocId = doc
          pqTop.segmentDocBase = docBase
          pqTop = pq.updateTop()
        }
      }
    }

  }

}
