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
  case class GraphTraversalPattern(src: Pattern, tr: Traversal, dst: Pattern) extends Pattern
  case class LazyRepetitionPattern(pattern: Pattern, min: Int, max: Option[Int]) extends Pattern
  case class GreedyRepetitionPattern(pattern: Pattern, min: Int, max: Option[Int]) extends Pattern
  case class FilterPattern(mainPattern: Pattern, filterPattern: Pattern) extends Pattern

  // FIXME should these be `Pattern` or something else?
  case class EventPattern(trigger: Pattern, arguments: List[ArgumentPattern]) extends Pattern
  case class ArgumentPattern(
    name: String,
    label: Option[String],
    fullTraversal: List[(Traversal, Pattern)],
    min: Int,
    max: Option[Int],
    promote: Boolean, // capture mention on-the-fly if not already captured
  ) extends Pattern

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
