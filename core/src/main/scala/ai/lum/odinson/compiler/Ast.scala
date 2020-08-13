package ai.lum.odinson.compiler

object Ast {

  sealed trait Matcher
  case class StringMatcher(string: String) extends Matcher
  case class RegexMatcher(pattern: String) extends Matcher

  sealed trait Constraint
  case object Wildcard extends Constraint
  case class FieldConstraint(name: String, matcher: Matcher) extends Constraint
  case class FuzzyConstraint(name: String, matcher: StringMatcher) extends Constraint
  case class NegatedConstraint(constraint: Constraint) extends Constraint
  case class ConjunctiveConstraint(constraints: List[Constraint]) extends Constraint
  case class DisjunctiveConstraint(constraints: List[Constraint]) extends Constraint

  sealed trait Assertion
  case object SentenceStartAssertion extends Assertion
  case object SentenceEndAssertion extends Assertion
  case class PositiveLookaheadAssertion(pattern: Pattern) extends Assertion
  case class NegativeLookaheadAssertion(pattern: Pattern) extends Assertion
  case class PositiveLookbehindAssertion(pattern: Pattern) extends Assertion
  case class NegativeLookbehindAssertion(pattern: Pattern) extends Assertion

  sealed trait Pattern
  case class AssertionPattern(assertion: Assertion) extends Pattern
  case class ConstraintPattern(constraint: Constraint) extends Pattern
  case class DisjunctivePattern(patterns: List[Pattern]) extends Pattern
  case class ConcatenatedPattern(patterns: List[Pattern]) extends Pattern
  case class NamedCapturePattern(name: String, label: Option[String], pattern: Pattern) extends Pattern
  case class MentionPattern(argName: Option[String], label: String) extends Pattern
  case class GraphTraversalPattern(src: Pattern, fullTraversal: FullTraversalPattern) extends Pattern
  case class LazyRepetitionPattern(pattern: Pattern, min: Int, max: Option[Int]) extends Pattern
  case class GreedyRepetitionPattern(pattern: Pattern, min: Int, max: Option[Int]) extends Pattern
  case class FilterPattern(mainPattern: Pattern, filterPattern: Pattern) extends Pattern

  // FIXME should these be `Pattern` or something else?
  case class EventPattern(trigger: Pattern, arguments: List[ArgumentPattern]) extends Pattern
  case class ArgumentPattern(
    name: String,
    label: Option[String],
    fullTraversal: FullTraversalPattern,
    min: Int,
    max: Option[Int],
    promote: Boolean, // capture mention on-the-fly if not already captured
  ) extends Pattern

  sealed trait FullTraversalPattern {
    def addMentionFilterToTerminals(mention: MentionPattern, allowPromotion: Boolean): FullTraversalPattern
    def isRequired: Boolean = true
  }

  case class SingleStepFullTraversalPattern(
    traversal: Traversal,
    surface: Pattern,
  ) extends FullTraversalPattern {
    def addMentionFilterToTerminals(mention: MentionPattern, allowPromotion: Boolean): FullTraversalPattern = {
      val newPattern =
        if (allowPromotion) {
          // if promotion is allowed then allow original pattern as well as the filtered one
          DisjunctivePattern(List(FilterPattern(mention, surface), surface))
        } else {
          // without promotion the mention is required
          FilterPattern(mention, surface)
        }
      SingleStepFullTraversalPattern(traversal, newPattern)
    }
  }

  case class ConcatFullTraversalPattern(
    clauses: List[FullTraversalPattern]
  ) extends FullTraversalPattern {
    def addMentionFilterToTerminals(mention: MentionPattern, allowPromotion: Boolean): FullTraversalPattern = {
      // traverse the list backwards adding filters until we find something required
      @annotation.tailrec
      def applyFilter(remaining: List[FullTraversalPattern], results: List[FullTraversalPattern]): List[FullTraversalPattern] = {
        remaining match {
          case Nil => results
          case head :: tail if head.isRequired =>
            val f = head.addMentionFilterToTerminals(mention, allowPromotion)
            results ++ List(f) ++ tail
          case head :: tail =>
            val f = head.addMentionFilterToTerminals(mention, allowPromotion)
            applyFilter(tail, results :+ f)
        }
      }
      val newTraversal = applyFilter(clauses.reverse, Nil).reverse
      ConcatFullTraversalPattern(newTraversal)
    }
  }

  case class RepeatFullTraversalPattern(
    min: Int, max: Int,
    fullTraversal: FullTraversalPattern,
  ) extends FullTraversalPattern {
    override def isRequired: Boolean = min > 0
    def addMentionFilterToTerminals(mention: MentionPattern, allowPromotion: Boolean): FullTraversalPattern = {
      (min, max) match {

        case (0, 0) =>
          ???

        case (0, 1) =>
          val step = fullTraversal.addMentionFilterToTerminals(mention, allowPromotion)
          RepeatFullTraversalPattern(0, 1, step)

        case (0, max) =>
          // newRep is all repetitions except the last one
          val newRep = RepeatFullTraversalPattern(min, max - 1, fullTraversal)
          // add filter to last repetition
          val lastStep = fullTraversal.addMentionFilterToTerminals(mention, allowPromotion)
          // stitch them together
          val pattern = ConcatFullTraversalPattern(List(newRep, lastStep))
          // make the whole thing optional
          RepeatFullTraversalPattern(0, 1, pattern)

        case (1, 1) =>
          fullTraversal.addMentionFilterToTerminals(mention, allowPromotion)

        case (min, max) =>
          // newRep is all repetitions except the last one
          val newRep = RepeatFullTraversalPattern(min - 1, max - 1, fullTraversal)
          // add filter to last repetition
          val lastStep = fullTraversal.addMentionFilterToTerminals(mention, allowPromotion)
          // stitch them together
          ConcatFullTraversalPattern(List(newRep, lastStep))

      }
    }
  }

  sealed trait Traversal
  case object NoTraversal extends Traversal
  case object OutgoingWildcard extends Traversal
  case object IncomingWildcard extends Traversal
  case class IncomingTraversal(matcher: Matcher) extends Traversal
  case class OutgoingTraversal(matcher: Matcher) extends Traversal
  case class ConcatenatedTraversal(traversals: List[Traversal]) extends Traversal
  case class DisjunctiveTraversal(traversals: List[Traversal]) extends Traversal
  case class OptionalTraversal(traversal: Traversal) extends Traversal
  case class KleeneStarTraversal(traversal: Traversal) extends Traversal

}
