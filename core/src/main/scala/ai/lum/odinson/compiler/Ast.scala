package ai.lum.odinson.compiler

import ai.lum.odinson.lucene.QuantifierType._

object Ast {

  sealed trait Matcher
  case class StringMatcher(string: String) extends Matcher
  case class RegexMatcher(pattern: String) extends Matcher

  sealed trait Constraint
  case object Wildcard extends Constraint
  case class FieldConstraint(name: String, matcher: Matcher) extends Constraint
  case class FuzzyConstraint(name: String, matcher: StringMatcher) extends Constraint
  case class NotConstraint(constraint: Constraint) extends Constraint
  case class AndConstraint(constraints: List[Constraint]) extends Constraint
  case class OrConstraint(constraints: List[Constraint]) extends Constraint

  sealed trait Pattern
  case object DocStartAssertion extends Pattern
  case object DocEndAssertion extends Pattern
  case class ConstraintPattern(constraint: Constraint) extends Pattern
  case class ConcatPattern(patterns: List[Pattern]) extends Pattern
  case class OrPattern(patterns: List[Pattern]) extends Pattern
  case class NamedPattern(pattern: Pattern, name: String) extends Pattern
  case class RangePattern(pattern: Pattern, min: Int, max: Int, quantifierType: QuantifierType) extends Pattern {
    require(min >= 0, "min can't be negative")
    require(min <= max, "min must be <= max")
  }
  case class GraphTraversalPattern(src: Pattern, tr: Traversal, dst: Pattern) extends Pattern

  sealed trait Traversal
  case object NoTraversal extends Traversal
  case object OutgoingWildcard extends Traversal
  case object IncomingWildcard extends Traversal
  case class IncomingTraversal(matcher: Matcher) extends Traversal
  case class OutgoingTraversal(matcher: Matcher) extends Traversal
  case class ConcatTraversal(traversals: List[Traversal]) extends Traversal
  case class OrTraversal(traversals: List[Traversal]) extends Traversal
  case class OptionalTraversal(traversal: Traversal) extends Traversal
  case class KleeneStarTraversal(traversal: Traversal) extends Traversal

}
