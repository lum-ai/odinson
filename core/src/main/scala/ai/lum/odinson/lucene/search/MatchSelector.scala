package ai.lum.odinson.lucene.search

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import ai.lum.odinson._

object MatchSelector {

  // implements algorithm to select match(es) based on specified query
  // e.g., greedy vs lazy, prefer leftmost clause of ORs
  // NOTE There could be more than one matches at the first starting point due to event unpacking
  def pickMatches(matches: Seq[OdinsonMatch]): List[OdinsonMatch] = {
    matches.foldRight(List.empty[OdinsonMatch]) {
      case (m1, m2 :: ms) => pickMatchFromPair(m1, m2) ::: ms
      case (m, ms) => m :: ms
    }
  }

  private def pickMatchFromPair(lhs: OdinsonMatch, rhs: OdinsonMatch): List[OdinsonMatch] = {
    @tailrec
    def traverse(left: List[OdinsonMatch], right: List[OdinsonMatch]): List[OdinsonMatch] = {
      (left, right) match {
        // left and right are both OR matches
        case ((l:OrMatch) :: lTail, (r:OrMatch) :: rTail) =>
          // if left is the leftmost clause then return lhs
          if (l.clauseID < r.clauseID) List(lhs)
          // if right is the leftmost clause then return rhs
          else if (l.clauseID > r.clauseID) List(rhs)
          // keep traversing the tree
          else traverse(l.subMatch :: lTail, r.subMatch :: rTail)

        // left and right are both optional
        case ((l:OptionalMatch) :: lTail, (r:OptionalMatch) :: rTail) =>
          if (l.isGreedy && r.isGreedy) {
            // if both are greedy return the longest
            if (l.length > r.length) List(lhs)
            else if (l.length < r.length) List(rhs)
            // if they are both the same length then keep going
            else traverse(l.subMatch :: lTail, r.subMatch :: rTail)
          } else if (r.isLazy && r.isLazy) {
            // if both are lazy return the shortest
            if (l.length < r.length) List(lhs)
            else if (l.length > r.length) List(rhs)
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
            if (l.length > r.length) List(lhs)
            else if (l.length < r.length) List(rhs)
            // if they are both the same length then keep going
            else traverse(l.subMatches ::: lTail, r.subMatches ::: rTail)
          } else if (l.isLazy && r.isLazy) {
            // if both are lazy return the shortest
            if (l.length < r.length) List(lhs)
            else if (l.length > r.length) List(rhs)
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

        case (Nil, Nil) =>
          List(lhs, rhs)

        case _ => ???

      }
    }
    traverse(List(lhs), List(rhs))
  }

  private def expandFirstMatch(ms: List[OdinsonMatch]): List[OdinsonMatch] = {
    ms match {
      case Nil => Nil
      case head :: tail => head match {
        case m: NGramMatch => tail
        case m: EventMatch => tail
        case m: OrMatch       => m.subMatch :: tail
        case m: NamedMatch    => m.subMatch :: tail
        case m: OptionalMatch => m.subMatch :: tail
        case m: ConcatMatch     => m.subMatches ::: tail
        case m: RepetitionMatch => m.subMatches ::: tail
        case m: GraphTraversalMatch => m.srcMatch :: m.dstMatch :: tail
      }
    }
  }

}