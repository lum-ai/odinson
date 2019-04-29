package ai.lum.odinson.lucene.search

import scala.collection.mutable.ArrayBuffer
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import org.apache.lucene.search.similarities.Similarity.SimScorer
import ai.lum.odinson.OdinsonMatch
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search.spans._

class OdinsonScorer(
    weight: OdinsonWeight,
    val spans: OdinsonSpans,
    val docScorer: SimScorer
) extends Scorer(weight) {

  private var accSloppyFreq: Float = 0 // accumulated sloppy freq (computed in setFreqCurrentDoc)
  private var numMatches: Int = 0      // number of matches (computed in setFreqCurrentDoc)
  private var lastScoredDoc: Int = -1  // last doc we called setFreqCurrentDoc() for

  // stores the matcher found in the current document
  private val collectedMatches: ArrayBuffer[OdinsonMatch] = ArrayBuffer.empty

  def getSpans(): OdinsonSpans = spans
  def docID(): Int = spans.docID()
  def iterator(): DocIdSetIterator = spans
  override def twoPhaseIterator(): TwoPhaseIterator = spans.asTwoPhaseIterator()

  private def setFreqCurrentDoc(): Unit = {
    accSloppyFreq = 0
    numMatches = 0
    collectedMatches.clear()

    spans.odinDoStartCurrentDoc()

    assert(spans.startPosition() == -1, "incorrect initial start position, " + spans)
    assert(spans.endPosition() == -1, "incorrect initial end position, " + spans)
    var prevStartPos = -1
    var prevEndPos = -1

    var startPos = spans.nextStartPosition()
    assert(startPos != Spans.NO_MORE_POSITIONS, "initial startPos NO_MORE_POSITIONS, " + spans)
    do {
      assert(startPos >= prevStartPos)
      val endPos = spans.endPosition()
      assert(endPos != Spans.NO_MORE_POSITIONS)
      assert((startPos != prevStartPos) || (endPos >= prevEndPos), "decreased endPos="+endPos)
      // if we already found a span starting at this position then discard it
      // because we only want to keep the longest
      if (startPos == prevStartPos) {
        collectedMatches.remove(collectedMatches.indices.last)
      }
      collectedMatches += spans.odinsonMatch // collect match
      numMatches += 1
      if (docScorer == null) {  // scores not required
        accSloppyFreq = 1
      } else {
        accSloppyFreq += docScorer.computeSlopFactor(spans.width())
      }
      spans.odinDoCurrentSpans()
      prevStartPos = startPos
      prevEndPos = endPos
      startPos = spans.nextStartPosition()
    } while (startPos != Spans.NO_MORE_POSITIONS)

    assert(spans.startPosition() == Spans.NO_MORE_POSITIONS, "incorrect final start position, " + spans)
    assert(spans.endPosition() == Spans.NO_MORE_POSITIONS, "incorrect final end position, " + spans)
  }

  private def scoreCurrentDoc(): Float = {
    // assert(docScorer != null, getClass() + " has a null docScorer!")
    // if the docscorer is null return zero
    // FIXME should we return one? are scores added or multiplied?
    if (docScorer == null) 0
    else docScorer.score(docID(), accSloppyFreq)
  }

  private def ensureFreq(): Unit = {
    val currentDoc = docID()
    if (lastScoredDoc != currentDoc) {
      setFreqCurrentDoc()
      lastScoredDoc = currentDoc
    }
  }

  def score(): Float = {
    ensureFreq()
    scoreCurrentDoc()
  }

  def freq(): Int = {
    ensureFreq()
    numMatches
  }

  def sloppyFreq(): Float = {
    ensureFreq()
    accSloppyFreq
  }

  def getMatches(): Array[OdinsonMatch] = {
    ensureFreq()
    collectedMatches.toArray
  }

}
