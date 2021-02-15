package ai.lum.odinson.lucene.search

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import ai.lum.common.itertools.product
import ai.lum.odinson._

object MatchSelector {

  // implements algorithm to select match(es) based on specified query
  // e.g., greedy vs lazy, prefer leftmost clause of ORs
  // NOTE There could be more than one matches at the first starting point due to event unpacking
  def pickMatches(matches: Seq[OdinsonMatch]): List[OdinsonMatch] = {
    val selectedMatches = matches.foldRight(List.empty[OdinsonMatch]) {
      case (m1, m2 :: ms) => pickMatchFromPair(m1, m2) ::: ms
      case (m, ms)        => m :: ms
    }
    selectedMatches.flatMap {
      case m: EventSketch => packageEvents(m)
      case m              => List(m)
    }
  }

  private def pickMatchFromPair(lhs: OdinsonMatch, rhs: OdinsonMatch): List[OdinsonMatch] = {
    @tailrec
    def traverse(left: List[OdinsonMatch], right: List[OdinsonMatch]): List[OdinsonMatch] = {
      (left, right) match {
        // left and right are both OR matches
        case ((l: OrMatch) :: lTail, (r: OrMatch) :: rTail) =>
          // if left is the leftmost clause then return lhs
          if (l.clauseID < r.clauseID) List(lhs)
          // if right is the leftmost clause then return rhs
          else if (l.clauseID > r.clauseID) List(rhs)
          // keep traversing the tree
          else traverse(l.subMatch :: lTail, r.subMatch :: rTail)

        // left and right are both optional
        case ((l: OptionalMatch) :: lTail, (r: OptionalMatch) :: rTail) =>
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
        case ((l: RepetitionMatch) :: lTail, (r: RepetitionMatch) :: rTail) =>
          if (l.isGreedy && r.isGreedy) {
            // if both are greedy return the longest
            if (l.length > r.length) List(lhs)
            else if (l.length < r.length) List(rhs)
            // if they are both the same length then keep going
            else traverse(l.subMatches.toList ::: lTail, r.subMatches.toList ::: rTail)
          } else if (l.isLazy && r.isLazy) {
            // if both are lazy return the shortest
            if (l.length < r.length) List(lhs)
            else if (l.length > r.length) List(rhs)
            // if they are both the same length then keep going
            else traverse(l.subMatches.toList ::: lTail, r.subMatches.toList ::: rTail)
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

    if (lhs.start == rhs.start) {
      if (lhs.end == rhs.end) {
        // if the matches are identical in terms of start and end, then they are
        // essentially equivalent, so return left because in Odinson semantics, as with regex,
        // the left hand side of the OR takes precedence.
        return List(lhs)
      }

      // if both mentions start at the same place then use our selection algorithm
      traverse(List(lhs), List(rhs))
    } else if (lhs.tokenInterval intersects rhs.tokenInterval) {
      // if they don't start at the same place but they intersect then choose the leftmost
      List(lhs)
    } else {
      // can't decide, return both
      List(lhs, rhs)
    }
  }

  private def expandFirstMatch(ms: List[OdinsonMatch]): List[OdinsonMatch] = {
    ms match {
      case Nil => Nil
      case head :: tail => head match {
          case m: NGramMatch          => tail
          case m: EventMatch          => ???
          case m: EventSketch         => m.trigger :: tail
          case m: OrMatch             => m.subMatch :: tail
          case m: NamedMatch          => m.subMatch :: tail
          case m: OptionalMatch       => m.subMatch :: tail
          case m: ConcatMatch         => m.subMatches.toList ::: tail
          case m: RepetitionMatch     => m.subMatches.toList ::: tail
          case m: GraphTraversalMatch => m.srcMatch :: m.dstMatch :: tail
          // what is a StateMatch? it may be a TextBound Mention or an Event Mention
          // if it's a TBMention, it should have same behavior as NGram
          case m: StateMatch if m.namedCaptures.isEmpty => tail
          // else, if an EventMention should have same behavior as EventSketch
          // the EventSketch returns it's trigger, here since it's an Event Mention,
          // we know that the start and end correspond to the trigger start and end
          case m: StateMatch => new NGramMatch(m.start, m.end) :: tail
        }
    }
  }

  /** get an event sketch and return a sequence of EventMatch objects */
  private def packageEvents(sketch: EventSketch): List[EventMatch] = {
    val trigger = sketch.trigger
    val argumentPackages = packageArguments(sketch.argSketches)
    argumentPackages.map(args => new EventMatch(trigger, args, sketch.argumentMetadata))
  }

  private def packageArguments(
    args: Array[(ArgumentSpans, OdinsonMatch)]
  ): List[Array[NamedCapture]] = {
    val packaged = args.groupBy(_._1).map { case (arg, values) =>
      // values is a sequence of (arg, match) tuples, so discard the arg
      val matches = values.map(_._2)
      packageArgument(arg, matches)
    }
    // return cartesian product of arguments
    product(packaged.toSeq).map(_.flatten.toArray).toList
  }

  private def packageArgument(
    arg: ArgumentSpans,
    allMatches: Seq[OdinsonMatch]
  ): Seq[Seq[NamedCapture]] = {
    val matches = for {
      g <- groupMatches(allMatches)
      m <- pickMatches(g)
    } yield m
    val packages = arg match {
      case ArgumentSpans(name, label, min, Some(max), _, _) if min == max =>
        // exact range (note that a range 1-1 means no quantifier)
        matches.combinations(min).toList
      case ArgumentSpans(name, label, min, Some(max), _, _) =>
        // range with min and max (note that min could be 0)
        if (matches.size < min) Nil
        else if (matches.size > max) matches.combinations(max).toList
        else Seq(matches)
      case ArgumentSpans(name, label, min, None, _, _) =>
        // at least min
        if (matches.size < min) Nil
        else Seq(matches)
    }
    for (pkg <- packages) yield {
      pkg.map(m => NamedCapture(arg.name, arg.label, m))
    }
  }

  private def groupMatches(matches: Seq[OdinsonMatch]): Seq[Seq[OdinsonMatch]] = {
    val buckets = ArrayBuffer.empty[ArrayBuffer[OdinsonMatch]]
    for (m <- matches) {
      var found = false
      var i = 0
      while (!found && i < buckets.size) {
        val b = buckets(i)
        i += 1
        // check if m belongs to the current bucket
        // (buckets should never be empty)
        if (m.tokenInterval intersects b.head.tokenInterval) {
          b += m
          found = true
        }
      }
      if (!found) {
        // add new bucket
        buckets += ArrayBuffer(m)
      }
    }
    buckets
  }

}
