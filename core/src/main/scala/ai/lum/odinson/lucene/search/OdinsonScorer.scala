package ai.lum.odinson.lucene.search

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import org.apache.lucene.search.similarities.Similarity.SimScorer
import ai.lum.odinson._
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search.spans._

class OdinsonScorer(
    weight: OdinsonWeight,
    val spans: OdinsonSpans,
    val docScorer: SimScorer
) extends Scorer(weight) {

  private var accSloppyFreq: Float = 0 // accumulated sloppy freq (computed in setFreqCurrentDoc)
  private var lastScoredDoc: Int = -1  // last doc we called setFreqCurrentDoc() for

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
    var currentMatch = getCurrentMatchAndAdvance(spans)
    while (currentMatch.isDefined) {
      collectedMatches += currentMatch.get
      // FIXME is sloppy frequency and doCurrentSpans() done once per survivor match? (i.e. here)
      // or once per overlapping match? (i.e. in getCurrentMatchAndAdvance)
      if (docScorer == null) {  // scores not required
        accSloppyFreq = 1
      } else {
        accSloppyFreq += docScorer.computeSlopFactor(spans.width())
      }
      spans.odinDoCurrentSpans()
      currentMatch = getCurrentMatchAndAdvance(spans)
    }
    assert(spans.startPosition() == Spans.NO_MORE_POSITIONS, "incorrect final start position, " + spans)
    assert(spans.endPosition() == Spans.NO_MORE_POSITIONS, "incorrect final end position, " + spans)
  }

  // - consumes all matches with the same start position
  // - selects the span to return
  // - leaves the spans iterator at the nest start position
  private def getCurrentMatchAndAdvance(spans: OdinsonSpans): Option[OdinsonMatch] = {
    val startPosition = spans.startPosition()
    if (startPosition == Spans.NO_MORE_POSITIONS) {
      return None
    }
    // gather all matches with the same start position
    val currentMatches = ArrayBuffer(spans.odinsonMatch)
    var nextStart = spans.nextStartPosition()
    while (nextStart == startPosition) {
      currentMatches += spans.odinsonMatch
      nextStart = spans.nextStartPosition()
    }
    // select final match
    val finalMatch = pickMatch(currentMatches)
    // advance to next match that doesn't overlap with current result
    while (nextStart != Spans.NO_MORE_POSITIONS && nextStart < finalMatch.end) {
      nextStart = spans.nextStartPosition()
    }
    // return result
    Some(finalMatch)
  }

  // implements algorithm to select match based on specified query
  // e.g., greedy vs lazy, prefer leftmost clause of ORs
  private def pickMatch(matches: ArrayBuffer[OdinsonMatch]): OdinsonMatch = {
    matches.reduce(pickMatchFromPair)
  }

  private def pickMatchFromPair(lhs: OdinsonMatch, rhs: OdinsonMatch): OdinsonMatch = {
    @tailrec
    def traverse(left: List[OdinsonMatch], right: List[OdinsonMatch]): OdinsonMatch = {
      (left, right) match {
        // left and right are both OR matches
        case ((l:OrMatch) :: lTail, (r:OrMatch) :: rTail) =>
          // if left is the leftmost clause then return lhs
          if (l.clauseID < r.clauseID) lhs
          // if right is the leftmost clause then return rhs
          else if (l.clauseID > r.clauseID) rhs
          // keep traversing the tree
          else traverse(l.subMatch :: lTail, r.subMatch :: rTail)

        // left and right are both optional
        case ((l:OptionalMatch) :: lTail, (r:OptionalMatch) :: rTail) =>
          if (l.isGreedy && r.isGreedy) {
            // if both are greedy return the longest
            if (l.length > r.length) lhs
            else if (l.length < r.length) rhs
            // if they are both the same length then keep going
            else traverse(l.subMatch :: lTail, r.subMatch :: rTail)
          } else if (r.isLazy && r.isLazy) {
            // if both are lazy return the shortest
            if (l.length < r.length) lhs
            else if (l.length > r.length) rhs
            // if they are both the same length then keep going
            else traverse(l.subMatch :: lTail, r.subMatch :: rTail)
          } else {
            // something is wrong
            ???
          }

        // left and right are both repetitions
        case ((l:RepetitionMatch) :: lTail, (r: RepetitionMatch) :: rTail) =>
          if (l.isGreedy && r.isGreedy) {
            // if both are greedy return the longest
            if (l.length > r.length) lhs
            else if (l.length < r.length) rhs
            // if they are both the same length then keep going
            else traverse(l.subMatches ::: lTail, r.subMatches ::: rTail)
          } else if (l.isLazy && r.isLazy) {
            // if both are lazy return the shortest
            if (l.length < r.length) lhs
            else if (l.length > r.length) rhs
            // if they are both the same length then keep going
            else traverse(l.subMatches ::: lTail, r.subMatches ::: rTail)
          } else {
            // something is wrong
            ???
          }

        case (l :: lTail, r :: rTail) =>
          val left = l match {
            case l: NGramMatch => lTail
            case l: GraphTraversalMatch => ???
            case l: ConcatMatch => l.subMatches ::: lTail
            case l: RepetitionMatch => ???
            case l: OptionalMatch => ???
            case l: OrMatch => ???
            case l: NamedMatch => l.subMatch :: lTail
          }
          val right = r match {
            case r: NGramMatch => rTail
            case r: GraphTraversalMatch => ???
            case r: ConcatMatch => r.subMatches ::: rTail
            case r: RepetitionMatch => ???
            case r: OptionalMatch => ???
            case r: OrMatch => ???
            case r: NamedMatch => r.subMatch :: rTail
          }
          if (left.isEmpty || right.isEmpty) {
            ???
          }
          traverse(left, right)
      }
    }
    traverse(List(lhs), List(rhs))
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

  def getMatches(): Array[OdinsonMatch] = {
    ensureMatchesCollected()
    collectedMatches.toArray
  }

}
