package ai.lum.odinson.lucene.search

import java.util.Arrays
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.util.PriorityQueue
import ai.lum.odinson.lucene._



class OdinsonCollector(
    private val collectedResults: Array[OdinsonScoreDoc],
    private val after: Int
) extends Collector {

  def this(numHits: Int, after: Int) = {
    this(new Array[OdinsonScoreDoc](numHits), after)
  }

  def this(numHits: Int) = {
    this(numHits, -1)
  }

  def this(numHits: Int, afterDoc: OdinsonScoreDoc) = {
    this(numHits, if (afterDoc == null) -1 else afterDoc.doc)
  }

  private var totalHits: Int = 0
  private var collectedHits: Int = 0

  override def needsScores(): Boolean = true

  def odinResults(): OdinResults = odinResults(0, collectedHits)

  def odinResults(start: Int): OdinResults = odinResults(start, collectedHits)

  def odinResults(start: Int, howMany: Int): OdinResults = {
    if (start < 0 || start >= collectedHits || howMany <= 0) {
      return OdinResults.empty
    }
    val fixedHowMany = math.min(collectedHits - start, howMany)
    val results = Arrays.copyOfRange(collectedResults, start, start + fixedHowMany)
    new OdinResults(totalHits, results)
  }

  abstract class OdinsonLeafCollector extends LeafCollector {
    protected var scorer: OdinsonScorer = null
    override def setScorer(scorer: Scorer): Unit = scorer match {
      case s: OdinsonScorer => this.scorer = s
      case _ => sys.error("unsupported scorer")
    }
  }

  def getLeafCollector(context: LeafReaderContext): LeafCollector = {
    val docBase = context.docBase
    val afterDoc = after - context.docBase
    new OdinsonLeafCollector {
      def collect(doc: Int): Unit = {
        totalHits += 1
        if (collectedHits >= collectedResults.length || doc <= afterDoc) {
          return
        }
        collectedResults(collectedHits) = new OdinsonScoreDoc(
          doc = doc + docBase,
          score = scorer.score(),
          shardIndex = -1,
          matches = scorer.getMatches(),
          segmentDocId = doc,
          segmentDocBase = docBase,
        )
        collectedHits += 1
      }
    }
  }

}
