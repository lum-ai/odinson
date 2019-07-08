package ai.lum.odinson.lucene.search

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import ai.lum.odinson._

object MatchSelector {

  // implements algorithm to select match based on specified query
  // e.g., greedy vs lazy, prefer leftmost clause of ORs
  def pickMatch(matches: ArrayBuffer[OdinsonMatch]): OdinsonMatch = {
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

        case (leftMatches, rightMatches) if leftMatches.nonEmpty && rightMatches.nonEmpty =>
          val left = expandFirstMatch(leftMatches)
          val right = expandFirstMatch(rightMatches)
          traverse(left, right)

        case (l, r) =>
          // if either the left or the right matches are empty
          // then something is wrong
          ???

      }
    }
    traverse(List(lhs), List(rhs))
  }

  private def expandFirstMatch(ms: List[OdinsonMatch]): List[OdinsonMatch] = {
    ms match {
      case Nil => Nil
      case head :: tail => head match {
        case m: NGramMatch => tail
        case m: NamedMatch => m.subMatch :: tail
        case m: ConcatMatch => m.subMatches ::: tail
        case m: GraphTraversalMatch => m.srcMatch :: m.dstMatch :: tail
        case m: RepetitionMatch => ???
        case m: OptionalMatch => ???
        case m: OrMatch => ???
      }
    }
  }

}
