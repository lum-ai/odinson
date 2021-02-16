package ai.lum.odinson.lucene.search

import scala.collection.mutable.ArrayBuffer
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import org.apache.lucene.search.similarities.Similarity.SimScorer
import ai.lum.odinson._
import ai.lum.odinson.lucene.search.spans._

class OdinsonScorer(
  weight: OdinsonWeight,
  val spans: OdinsonSpans,
  val docScorer: SimScorer
) extends Scorer(weight) {

  private var accSloppyFreq: Float = 0 // accumulated sloppy freq (computed in setFreqCurrentDoc)
  private var lastScoredDoc: Int = -1 // last doc we called setFreqCurrentDoc() for

  // stores the matcher found in the current document
  private val collectedMatches: ArrayBuffer[OdinsonMatch] = ArrayBuffer.empty

  def getSpans(): OdinsonSpans = spans
  def docID(): Int = spans.docID()
  def iterator(): DocIdSetIterator = spans
  override def twoPhaseIterator(): TwoPhaseIterator = spans.asTwoPhaseIterator()

  private def collectMatchesCurrentDoc(): Unit = {
    accSloppyFreq = 0
    collectedMatches.clear()
    spans.odinDoStartCurrentDoc()
    // get to first match
    spans.nextStartPosition()
    var currentMatches = getCurrentMatchesAndAdvance(spans)
    while (currentMatches.nonEmpty) {
      collectedMatches ++= currentMatches
      // FIXME is sloppy frequency and doCurrentSpans() done once per survivor match? (i.e. here)
      // or once per overlapping match? (i.e. in getCurrentMatchAndAdvance)
      if (docScorer == null) { // scores not required
        accSloppyFreq = 1
      } else {
        accSloppyFreq += docScorer.computeSlopFactor(spans.width())
      }
      spans.odinDoCurrentSpans()
      currentMatches = getCurrentMatchesAndAdvance(spans)
    }
    assert(
      spans.startPosition() == Spans.NO_MORE_POSITIONS,
      "incorrect final start position, " + spans
    )
    assert(spans.endPosition() == Spans.NO_MORE_POSITIONS, "incorrect final end position, " + spans)
  }

  // - consumes all matches with the same start position
  // - selects the span to return
  // - leaves the spans iterator at the next start position
  private def getCurrentMatchesAndAdvance(spans: OdinsonSpans): Seq[OdinsonMatch] = {
    val startPosition = spans.startPosition()
    if (startPosition == Spans.NO_MORE_POSITIONS) {
      return Nil
    }
    // gather all matches with the same start position
    val currentMatches = ArrayBuffer.empty[OdinsonMatch]
    var nextStart = spans.startPosition()
    while (nextStart == startPosition) {
      currentMatches += spans.odinsonMatch
      nextStart = spans.nextStartPosition()
    }
    // select final match
    val finalMatches = MatchSelector.pickMatches(currentMatches)
    // advance to next match that doesn't overlap with current result
    while (nextStart != Spans.NO_MORE_POSITIONS && nextStart < finalMatches.last.end) {
      nextStart = spans.nextStartPosition()
    }
    // return results
    finalMatches
  }

  private def scoreCurrentDoc(): Float = {
    // assert(docScorer != null, getClass() + " has a null docScorer!")
    // if the docscorer is null return zero
    // FIXME should we return one? are scores added or multiplied?
    if (docScorer == null) 0
    else docScorer.score(docID(), accSloppyFreq)
  }

  private def ensureMatchesCollected(): Unit = {
    val currentDoc = docID()
    if (lastScoredDoc != currentDoc) {
      collectMatchesCurrentDoc()
      lastScoredDoc = currentDoc
    }
  }

  def score(): Float = {
    ensureMatchesCollected()
    scoreCurrentDoc()
  }

  def freq(): Int = {
    ensureMatchesCollected()
    // FIXME returns number of surviving matches
    // but should it return total matches? (overlapping)
    collectedMatches.length
  }

  def sloppyFreq(): Float = {
    ensureMatchesCollected()
    accSloppyFreq
  }

  /** Returns the actual matches for the current doc. */
  def getMatches(): Array[OdinsonMatch] = {
    ensureMatchesCollected()
    collectedMatches.toArray
  }

  /** Returns all possible matches for the current doc.
    *
    *  All (possibly overlapping) sequences of tokens that
    *  could be considered a match for the given query
    *  will be included in the results.
    *
    *  NOTE don't call this method and getMatches()
    *  on the same scorer instance.
    */
  def getAllPossibleMatches(): Array[OdinsonMatch] = {
    ensureAllPossibleMatchesCollected()
    collectedMatches.toArray
  }

  private def ensureAllPossibleMatchesCollected(): Unit = {
    val currentDoc = docID()
    if (lastScoredDoc != currentDoc) {
      collectAllPossibleMatchesCurrentDoc()
      lastScoredDoc = currentDoc
    }
  }

  private def collectAllPossibleMatchesCurrentDoc(): Unit = {
    accSloppyFreq = 0
    collectedMatches.clear()
    spans.odinDoStartCurrentDoc()
    spans.nextStartPosition()
    while (spans.startPosition() != Spans.NO_MORE_POSITIONS) {
      collectedMatches += spans.odinsonMatch
      if (docScorer == null) {
        accSloppyFreq = 1
      } else {
        accSloppyFreq += docScorer.computeSlopFactor(spans.width())
      }
      spans.odinDoCurrentSpans()
      spans.nextStartPosition()
    }
  }

}
