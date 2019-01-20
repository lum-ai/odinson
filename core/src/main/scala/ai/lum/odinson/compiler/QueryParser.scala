package ai.lum.odinson.compiler

import fastparse.noApi._
import Whitespace._
import Ast._

class QueryParser(
    val allTokenFields: Seq[String], // the names of all valid token fields
    val defaultTokenField: String,   // the name of the default token field
    val lowerCaseQueriesToDefaultField: Boolean
) {

  // parser's entry point
  def parse(query: String): Pattern = odinsonPattern.parse(query).get.value

  // grammar's top-level symbol
  val odinsonPattern: P[Pattern] = {
    P(Start ~ (graphTraversalPattern | surfacePattern) ~ End)
  }

  val surfacePattern: P[Pattern] = {
    P(disjunctivePattern)
  }

  val graphTraversalPattern: P[Ast.Pattern] = {
    P(atomicPattern ~ (disjunctiveTraversal ~ atomicPattern).rep(1)).map {
      case (src, rest) => rest.foldLeft(src) {
        case (lhs, (tr, rhs)) => Ast.GraphTraversalPattern(lhs, tr, rhs)
      }
    }
  }

  val disjunctivePattern: P[Ast.Pattern] = {
    P(concatenatedPattern.rep(min = 1, sep = "|")).map {
      case Seq(pattern) => pattern
      case patterns     => Ast.DisjunctivePattern(patterns.toList)
    }
  }

  val concatenatedPattern: P[Ast.Pattern] = {
    P(quantifiedPattern.rep(1)).map {
      case Seq(pattern) => pattern
      case patterns     => Ast.ConcatenatedPattern(patterns.toList)
    }
  }

  val quantifiedPattern: P[Ast.Pattern] = {
    P(atomicPattern ~ quantifier(includeLazy = false).?).map {
      case (pattern, None) => pattern
      case (pattern, Some(GreedyQuantifier(min, max))) => Ast.GreedyRepetitionPattern(pattern, min, max)
      case (pattern, Some(LazyQuantifier(min, max)))   => Ast.LazyRepetitionPattern(pattern, min, max)
    }
  }

  val atomicPattern: P[Ast.Pattern] = {
    P(constraintPattern | mentionPattern | "(" ~ disjunctivePattern ~ ")" | namedCapturePattern | assertionPattern)
  }

  val mentionPattern: P[Ast.Pattern] = {
    // NOTE: Arg name is optional
    P("@" ~ (Literals.javaIdentifier ~ ":").? ~ Literals.string).map{
      case (Some(argName), label) => Ast.MentionPattern(argName = Some(argName), label = label)
      case (None, label) => Ast.MentionPattern(argName = None, label = label)
    }
  }

  val namedCapturePattern: P[Ast.Pattern] = {
    P("(?<" ~ Literals.javaIdentifier.! ~ ">" ~ disjunctivePattern ~ ")").map {
      case (name, pattern) => Ast.NamedCapturePattern(name, pattern)
    }
  }

  val constraintPattern: P[Ast.Pattern] = {
    P(tokenConstraint).map(Ast.ConstraintPattern)
  }

  val assertionPattern: P[Ast.Pattern] = {
    P(sentenceStart | sentenceEnd | lookaround).map(Ast.AssertionPattern)
  }

  ///////////////////////////
  //
  // quantifiers
  //
  ///////////////////////////

  sealed trait Quantifier
  case class GreedyQuantifier(min: Int, max: Option[Int]) extends Quantifier
  case class LazyQuantifier(min: Int, max: Option[Int]) extends Quantifier

  def quantifier(includeLazy: Boolean): P[Quantifier] = {
    P(quantOperator(includeLazy) | range(includeLazy) | repetition)
  }

  def quantOperator(includeLazy: Boolean): P[Quantifier] = {
    val operator = if (includeLazy) StringIn("?", "*", "+", "??", "*?", "+?") else StringIn("?", "*", "+")
    P(operator.!).map {
      case "?"  => GreedyQuantifier(0, Some(1))
      case "*"  => GreedyQuantifier(0, None)
      case "+"  => GreedyQuantifier(1, None)
      case "??" => LazyQuantifier(0, Some(1))
      case "*?" => LazyQuantifier(0, None)
      case "+?" => LazyQuantifier(1, None)
      case _ => ??? // this shouldn't happen
    }
  }

  def range(includeLazy: Boolean): P[Quantifier] = {
    val operator = if (includeLazy) StringIn("}", "}?") else StringIn("}")
    P("{" ~ Literals.unsignedInt.? ~ "," ~ Literals.unsignedInt.? ~ operator.!).map {
      case (Some(min), Some(max), _) if min > max => sys.error("min should be less than or equal to max")
      case (Some(min), maxOption, "}")  => GreedyQuantifier(min, maxOption)
      case (None,      maxOption, "}")  => GreedyQuantifier(0, maxOption)
      case (Some(min), maxOption, "}?") => LazyQuantifier(min, maxOption)
      case (None,      maxOption, "}?") => LazyQuantifier(0, maxOption)
    }
  }

  val repetition: P[Quantifier] = {
    P("{" ~ Literals.unsignedInt ~ "}").map {
      case n => GreedyQuantifier(n, Some(n))
    }
  }

  ///////////////////////////
  //
  // assertions
  //
  ///////////////////////////

  val sentenceStart: P[Ast.Assertion] = {
    P("<s>").map(_ => Ast.SentenceStartAssertion)
  }

  val sentenceEnd: P[Ast.Assertion] = {
    P("</s>").map(_ => Ast.SentenceEndAssertion)
  }

  val lookaround: P[Ast.Assertion] = {
    P(StringIn("(?=", "(?!", "(?<=", "(?<!").! ~ disjunctivePattern ~ ")").map {
      case ("(?=",  pattern) => Ast.PositiveLookaheadAssertion(pattern)
      case ("(?!",  pattern) => Ast.NegativeLookaheadAssertion(pattern)
      case ("(?<=", pattern) => Ast.PositiveLookbehindAssertion(pattern)
      case ("(?<!", pattern) => Ast.NegativeLookbehindAssertion(pattern)
      case _ => ???
    }
  }

  ///////////////////////////
  //
  // graph traversals
  //
  ///////////////////////////

  def disjunctiveTraversal: P[Ast.Traversal] = {
    P(concatenatedTraversal.rep(min = 1, sep = "|")).map {
      case Seq(traversal) => traversal
      case traversals     => Ast.DisjunctiveTraversal(traversals.toList)
    }
  }

  val concatenatedTraversal: P[Ast.Traversal] = {
    P(quantifiedTraversal.rep(1)).map {
      case Seq(traversal) => traversal
      case traversals     => Ast.ConcatenatedTraversal(traversals.toList)
    }
  }

  /** helper function that repeats a pattern N times */
  private def repeat[T](t: T, n: Int): List[T] = List.fill(n)(t)

  val quantifiedTraversal: P[Ast.Traversal] = {
    P(atomicTraversal ~ quantifier(includeLazy = false).?).map {
      case (traversal, None) => traversal
      case (traversal, Some(GreedyQuantifier(1, Some(1)))) => traversal
      case (_,         Some(GreedyQuantifier(0, Some(0)))) => Ast.NoTraversal
      case (traversal, Some(GreedyQuantifier(0, Some(1)))) => Ast.OptionalTraversal(traversal)
      case (traversal, Some(GreedyQuantifier(0, None)))    => Ast.KleeneStarTraversal(traversal)
      case (traversal, Some(GreedyQuantifier(1, None)))    => Ast.ConcatenatedTraversal(List(traversal, Ast.KleeneStarTraversal(traversal)))
      case (traversal, Some(GreedyQuantifier(n, None)))    => Ast.ConcatenatedTraversal(repeat(traversal, n) :+ Ast.KleeneStarTraversal(traversal))
      case (traversal, Some(GreedyQuantifier(m, Some(n)))) if m == n => Ast.ConcatenatedTraversal(repeat(traversal, n))
      case (traversal, Some(GreedyQuantifier(m, Some(n)))) if m < n => Ast.ConcatenatedTraversal(repeat(traversal, m) ++ repeat(Ast.OptionalTraversal(traversal), n - m))
      case _ => throw new Exception("Unrecognized quantifier")
    }
  }

  val atomicTraversal: P[Ast.Traversal] = {
    P(singleStepTraversal | "(" ~ disjunctiveTraversal ~ ")")
  }

  val singleStepTraversal: P[Ast.Traversal] = {
    P(outgoingTraversal | incomingTraversal | outgoingWildcard | incomingWildcard)
  }

  val incomingWildcard: P[Ast.Traversal] = {
    P("<<").map(_ => Ast.IncomingWildcard)
  }

  val incomingTraversal: P[Ast.Traversal] = {
    P("<" ~ stringMatcher).map(Ast.IncomingTraversal)
  }

  val outgoingWildcard: P[Ast.Traversal] = {
    P(">>").map(_ => Ast.OutgoingWildcard)
  }

  val outgoingTraversal: P[Ast.Traversal] = {
    P(">" ~ stringMatcher).map(Ast.OutgoingTraversal)
  }

  ///////////////////////////
  //
  // token constraints
  //
  ///////////////////////////

  val tokenConstraint: P[Ast.Constraint] = {
    P(explicitConstraint | defaultFieldConstraint)
  }

  val defaultFieldConstraint: P[Ast.Constraint] = {
    P(defaultFieldRegexConstraint | defaultFieldStringConstraint)
  }

  val defaultFieldStringConstraint: P[Ast.Constraint] = {
    P(stringMatcher ~ "~".!.?).map {
      case (matcher, None)    => Ast.FieldConstraint(defaultTokenField, matcher)
      case (matcher, Some(_)) => Ast.FuzzyConstraint(defaultTokenField, matcher)
    }
  }

  val defaultFieldRegexConstraint: P[Ast.Constraint] = {
    P(regexMatcher).map(matcher => Ast.FieldConstraint(defaultTokenField, matcher))
  }

  val explicitConstraint: P[Ast.Constraint] = {
    P("[" ~ disjunctiveConstraint.? ~ "]").map {
      case None             => Ast.Wildcard
      case Some(constraint) => constraint
    }
  }

  val disjunctiveConstraint: P[Ast.Constraint] = {
    P(conjunctiveConstraint.rep(min = 1, sep = "|")).map {
      case Seq(constraint) => constraint
      case constraints     => Ast.DisjunctiveConstraint(constraints.toList)
    }
  }

  val conjunctiveConstraint: P[Ast.Constraint] = {
    P(negatedConstraint.rep(min = 1, sep = "&")).map {
      case Seq(constraint) => constraint
      case constraints     => Ast.ConjunctiveConstraint(constraints.toList)
    }
  }

  val negatedConstraint: P[Ast.Constraint] = {
    P("!".!.? ~ atomicConstraint).map {
      case (None, constraint)    => constraint
      case (Some(_), constraint) => Ast.NegatedConstraint(constraint)
    }
  }

  val atomicConstraint: P[Ast.Constraint] = {
    P(fieldConstraint | "(" ~ disjunctiveConstraint ~ ")")
  }

  val fieldConstraint: P[Ast.Constraint] = {
    P(regexFieldConstraint | stringFieldConstraint)
  }

  val regexFieldConstraint: P[Ast.Constraint] = {
    P(fieldName ~ StringIn("=", "!=").! ~ regexMatcher).map {
      case (name, "=",  matcher) => Ast.FieldConstraint(name, matcher)
      case (name, "!=", matcher) => Ast.NegatedConstraint(Ast.FieldConstraint(name, matcher))
      case _ => ??? // this shouldn't happen
    }
  }

  val stringFieldConstraint: P[Ast.Constraint] = {
    P(fieldName ~ StringIn("=", "!=").! ~ stringMatcher ~ QueryParser.FUZZY_SYMBOL.!.?).map {
      case (name, "=",  matcher, None)    => Ast.FieldConstraint(name, matcher)
      case (name, "!=", matcher, None)    => Ast.NegatedConstraint(Ast.FieldConstraint(name, matcher))
      case (name, "=",  matcher, Some(_)) => Ast.FuzzyConstraint(name, matcher)
      case (name, "!=", matcher, Some(_)) => Ast.NegatedConstraint(Ast.FuzzyConstraint(name, matcher))
      case _ => ??? // this shouldn't happen
    }
  }

  // any value in `allTokenFields` is a valid field name
  val fieldName: P[String] = {
    P(StringIn(allTokenFields: _*)).!
  }

  val anyMatcher: P[Ast.Matcher] = {
    P(stringMatcher | regexMatcher)
  }

  val stringMatcher: P[Ast.StringMatcher] = {
    P(Literals.string.map(Ast.StringMatcher))
  }

  val regexMatcher: P[Ast.RegexMatcher] = {
    P(Literals.regex.map(Ast.RegexMatcher))
  }

}

object QueryParser {
  val FUZZY_SYMBOL = "~"
}
