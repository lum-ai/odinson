package ai.lum.odinson.lucene.search

import java.util.Arrays
import org.apache.lucene.index._
import org.apache.lucene.search._
import ai.lum.odinson.lucene._
import org.apache.lucene.search.CollectionTerminatedException


class OdinsonCollector(
  private val collectedResults: Array[OdinsonScoreDoc],
  private val after: Int,
  private val computeTotalHits: Boolean,
  private val disableMatchSelector: Boolean,
) extends Collector {

  def this(numHits: Int, after: Int, computeTotalHits: Boolean, disableMatchSelector: Boolean) = {
    this(new Array[OdinsonScoreDoc](numHits), after, computeTotalHits, disableMatchSelector)
  }

  def this(numHits: Int, computeTotalHits: Boolean, disableMatchSelector: Boolean) = {
    this(numHits, -1, computeTotalHits, disableMatchSelector)
  }

  def this(numHits: Int, afterDoc: OdinsonScoreDoc, computeTotalHits: Boolean, disableMatchSelector: Boolean) = {
    this(numHits, if (afterDoc == null) -1 else afterDoc.doc, computeTotalHits, disableMatchSelector)
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

  case class TotalHitsCalculatingLeafCollector(
    docBase: Int,
    afterDoc: Int,
  ) extends OdinsonLeafCollector {
    def collect(doc: Int): Unit = {
      totalHits += 1
      if (collectedHits >= collectedResults.length || doc <= afterDoc) {
        // don't terminate, we want to keep collecting
        // for accurate totalHits count
        return
      }
      val matches = if (disableMatchSelector) scorer.getAllPossibleMatches() else scorer.getMatches()
      collectedResults(collectedHits) = new OdinsonScoreDoc(
        doc = doc + docBase,
        score = scorer.score(),
        shardIndex = -1,
        matches = matches,
        segmentDocId = doc,
        segmentDocBase = docBase,
      )
      collectedHits += 1
    }
  }

  case class EarlyTerminationLeafCollector(
    docBase: Int,
    afterDoc: Int,
  ) extends OdinsonLeafCollector {
    def collect(doc: Int): Unit = {
      if (collectedHits >= collectedResults.length) {
        // terminate, since all required results have been collected
        throw new CollectionTerminatedException()
      }
      if (doc <= afterDoc) {
        return
      }
      val matches = if (disableMatchSelector) scorer.getAllPossibleMatches() else scorer.getMatches()
      collectedResults(collectedHits) = new OdinsonScoreDoc(
        doc = doc + docBase,
        score = scorer.score(),
        shardIndex = -1,
        matches = matches,
        segmentDocId = doc,
        segmentDocBase = docBase,
      )
      collectedHits += 1
      totalHits += 1
    }
  }

  case class NOPCollector() extends LeafCollector {
    def setScorer(scorer: Scorer): Unit = {}

    def collect(doc: Int): Unit = throw new CollectionTerminatedException()
  }

  def getLeafCollector(context: LeafReaderContext): LeafCollector = {
    val docBase = context.docBase
    val afterDoc = after - context.docBase

    if (computeTotalHits) {
      TotalHitsCalculatingLeafCollector(docBase, afterDoc)
    } else {
      var skipEntireSegment = false

      // based on the docBase of the next reader in line, we might want to skip this entire reader
      // if all the indexes here are before the specified 'after' value
      if (context.parent.isTopLevel && context.parent.leaves().size() > context.ordInParent + 1) {
        val nextLeafContext = context.parent.leaves().get(context.ordInParent + 1)
        if (nextLeafContext.docBase <= after + 1) {
          skipEntireSegment = true
        }
      }

      if (skipEntireSegment) {
        NOPCollector()
      } else {
        EarlyTerminationLeafCollector(docBase, afterDoc)
      }
    }
  }

}
