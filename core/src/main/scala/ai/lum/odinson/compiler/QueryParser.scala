package ai.lum.odinson.compiler

import fastparse.noApi._
import Whitespace._
import Ast._
import ai.lum.odinson.lucene.QuantifierType._

class QueryParser(
    val allTokenFields: Seq[String], // the names of all valid token fields
    val defaultTokenField: String,   // the name of the default token field
    val lowerCaseQueriesToDefaultField: Boolean
) {

  // parser's entry point
  def parse(query: String): Pattern = pattern.parse(query).get.value

  // grammar's top-level symbol
  val pattern: P[Pattern] = P(Start ~ graphTraversalPattern ~ End)

  val graphTraversalPattern: P[Pattern] = P(orPattern ~ (orTraversal ~ orPattern).rep).map {
    case (src, rest) => rest.foldLeft(src) {
      case (lhs, (tr, rhs)) => GraphTraversalPattern(lhs, tr, rhs)
    }
  }

  val orPattern: P[Pattern] = P(concatPattern.rep(min = 1, sep = "|")).map {
    case Seq(pat) => pat
    case pats => OrPattern(pats.toList)
  }

  val concatPattern: P[Pattern] = P(patternWithRepetition.rep(1)).map {
    case Seq(pat) => pat
    case pats => ConcatPattern(pats.toList)
  }

  val patternWithRepetition: P[Pattern] = {
    P(quantifiedPattern | rangePattern | repeatedPattern | atomicPattern)
  }

  val namedPattern: P[Pattern] = P("(?<" ~ Literals.javaIdentifier ~ ">" ~ orPattern ~ ")").map {
    case (name, pattern) => NamedPattern(pattern, name)
  }

  // zero-width assertions
  val zeroWidthAssertion: P[Pattern] = P(docStartAssertion | docEndAssertion)
  val docStartAssertion: P[Pattern] = P("<s>").map(_ => DocStartAssertion)
  val docEndAssertion: P[Pattern] = P("</s>").map(_ => DocEndAssertion)

  val atomicPattern: P[Pattern] = P(termPattern | namedPattern | zeroWidthAssertion | "(" ~ orPattern ~ ")")

  val quantifiedPattern: P[Pattern] = P(atomicPattern ~ ("??" | "*?" | "+?" | "?" | "*" | "+").!).map {
    case (pattern, "?")  => RangePattern(pattern, 0, 1,            Greedy)
    case (pattern, "*")  => RangePattern(pattern, 0, Int.MaxValue, Greedy)
    case (pattern, "+")  => RangePattern(pattern, 1, Int.MaxValue, Greedy)
    case (pattern, "??") => RangePattern(pattern, 0, 1,            Lazy)
    case (pattern, "*?") => RangePattern(pattern, 0, Int.MaxValue, Lazy)
    case (pattern, "+?") => RangePattern(pattern, 1, Int.MaxValue, Lazy)
    case _ => ??? // silence a compiler warning (only possible strings are ?, *, +, ??, *?, +?)
  }

  val rangePattern: P[Pattern] = {
    P(atomicPattern ~ "{" ~ Literals.unsignedInt.? ~ "," ~ Literals.unsignedInt.? ~ ("}?" | "}").!).map {
      case (pattern, None,      None,      "}")  => RangePattern(pattern, 0,   Int.MaxValue, Greedy)
      case (pattern, None,      Some(max), "}")  => RangePattern(pattern, 0,   max,          Greedy)
      case (pattern, Some(min), None,      "}")  => RangePattern(pattern, min, Int.MaxValue, Greedy)
      case (pattern, Some(min), Some(max), "}")  => RangePattern(pattern, min, max,          Greedy)
      case (pattern, None,      None,      "}?") => RangePattern(pattern, 0,   Int.MaxValue, Lazy)
      case (pattern, None,      Some(max), "}?") => RangePattern(pattern, 0,   max,          Lazy)
      case (pattern, Some(min), None,      "}?") => RangePattern(pattern, min, Int.MaxValue, Lazy)
      case (pattern, Some(min), Some(max), "}?") => RangePattern(pattern, min, max,          Lazy)
      case _ => ??? // silence a compiler warning (only possible strings are } and }?)
    }
  }

  val repeatedPattern: P[Pattern] = P(atomicPattern ~ "{" ~ Literals.unsignedInt ~ "}").map {
    case (pattern, n) => RangePattern(pattern, n, n, Lazy)
  }

  val termPattern: P[Pattern] = P(explicitTermPattern | wildcards)

  val explicitTermPattern: P[Pattern] = P(tokenConstraint map ConstraintPattern)

  val wildcards: P[Pattern] = P("[".! ~ "]").rep(min = 1).map { brackets =>
    val n = brackets.length
    val wildcard = ConstraintPattern(Wildcard)
    if (n == 1) wildcard
    else RangePattern(wildcard, n, n, Lazy)
  }

  val tokenConstraint: P[Constraint] = {
    P("!".!.? ~ (defaultFieldConstraint | explicitConstraint)).map {
      case (None, constraint) => constraint
      case (Some(_), constraint) => NotConstraint(constraint)
    }
  }

  val defaultFieldConstraint: P[Constraint] = P(defaultFieldRegexConstraint | defaultFieldStringConstraint)

  val defaultFieldRegexConstraint: P[Constraint] = P(Literals.regex).map {
    case pattern => FieldConstraint(defaultTokenField, RegexMatcher(maybeLowerCase(pattern)))
  }

  // only exact string matchers can be fuzzified, as opposed to regex string matchers
  val defaultFieldStringConstraint: P[Constraint] = P(Literals.string ~ "~".!.?).map {
    case (string, None) => FieldConstraint(defaultTokenField, StringMatcher(maybeLowerCase(string)))
    case (string, Some(_)) => FuzzyConstraint(defaultTokenField, StringMatcher(maybeLowerCase(string)))
  }

  val explicitConstraint: P[Constraint] = P("[" ~ orConstraint ~ "]")

  val orConstraint: P[Constraint] = P(andConstraint.rep(min = 1, sep = "|")).map {
    case Seq(constraint) => constraint
    case constraints => OrConstraint(constraints.toList)
  }

  val andConstraint: P[Constraint] = P(notConstraint.rep(min = 1, sep = "&")).map {
    case Seq(constraint) => constraint
    case constraints => AndConstraint(constraints.toList)
  }

  val notConstraint: P[Constraint] = P("!".!.? ~ atomicConstraint).map {
    case (None, constraint) => constraint
    case (Some(_), constraint) => NotConstraint(constraint)
  }

  val atomicConstraint: P[Constraint] = P(fieldConstraint | "(" ~ orConstraint ~ ")")

  val fieldConstraint = P(regexFieldConstraint | stringFieldConstraint)

  val regexFieldConstraint = P(fieldName ~ ("="|"!=").! ~ regexStringMatcher).map {
    case (name, "=", matcher) => FieldConstraint(name, matcher)
    case (name, "!=", matcher) => NotConstraint(FieldConstraint(name, matcher))
    case _ => ??? // there is no other operator besides = and != but the compiler doesn't know
  }

  val stringFieldConstraint = P(fieldName ~ ("="|"!=").! ~ exactStringMatcher ~ "~".!.?).map {
    case (name, "=", matcher, None) => FieldConstraint(name, matcher)
    case (name, "!=", matcher, None) => NotConstraint(FieldConstraint(name, matcher))
    case (name, "=", matcher, Some(_)) => FuzzyConstraint(name, matcher)
    case (name, "!=", matcher, Some(_)) => NotConstraint(FuzzyConstraint(name, matcher))
    case _ => ???
  }

  // any value in `allTokenFields` is a valid field name
  val fieldName: P[String] = P(StringIn(allTokenFields:_*)).!

  val stringMatcher: P[Matcher] = P(exactStringMatcher | regexStringMatcher)

  val exactStringMatcher: P[StringMatcher] = P(Literals.string.map(StringMatcher))

  val regexStringMatcher: P[RegexMatcher] = P(Literals.regex.map(RegexMatcher))

  /** used to decide if queries to the default field should be lowercased first */
  private def maybeLowerCase(s: String): String = {
    if (lowerCaseQueriesToDefaultField) s.toLowerCase else s
  }

  // graph traversal

  val incomingWildcard: P[Traversal] = P("<<").map(_ => IncomingWildcard)
  val incomingTraversal: P[Traversal] = P("<" ~ stringMatcher).map(IncomingTraversal)
  val outgoingWildcard: P[Traversal] = P(">>").map(_ => OutgoingWildcard)
  val outgoingTraversal: P[Traversal] = P(">" ~ stringMatcher).map(OutgoingTraversal)

  val oneStepTraversal: P[Traversal] = P(incomingTraversal | incomingWildcard | outgoingTraversal | outgoingWildcard)

  val atomicTraversal: P[Traversal] = P(oneStepTraversal | "(" ~ orTraversal ~ ")")

  val quantifiedTraversal: P[Traversal] = P(atomicTraversal ~ ("?" | "*" | "+").!).map {
    case (traversal, "?") => OptionalTraversal(traversal)
    case (traversal, "*") => KleeneStarTraversal(traversal)
    case (traversal, "+") => ConcatTraversal(List(traversal, KleeneStarTraversal(traversal)))
    case _ => ??? // this is here to silence a compiler warning
  }

  /** helper function that repeats a pattern N times */
  private def repeat[T](t: T, n: Int): List[T] = List.fill(n)(t)

  val repeatedTraversal: P[Traversal] = P(atomicTraversal ~ "{" ~ Literals.unsignedInt ~ "}").map {
    case (traversal@_, 0) => NoTraversal
    case (traversal, 1) => traversal
    case (traversal, n) => ConcatTraversal(repeat(traversal, n))
  }

  val rangedTraversal: P[Traversal] = {
    P(atomicTraversal ~ "{" ~ Literals.unsignedInt.? ~ "," ~/ Literals.unsignedInt.? ~ "}")
      .filter { // fail if m > n
        case (_, Some(m), Some(n)) => m <= n
        case _ => true
      }
      .map {
        case (traversal,   Some(1),        Some(1)) => traversal
        case (traversal@_, Some(0) | None, Some(0)) => NoTraversal
        case (traversal,   Some(0) | None, Some(1)) => OptionalTraversal(traversal)
        case (traversal,   Some(0) | None, Some(n)) => ConcatTraversal(repeat(OptionalTraversal(traversal), n))
        case (traversal,   Some(0) | None, None)    => KleeneStarTraversal(traversal)
        case (traversal,   Some(m),        None)    => ConcatTraversal(repeat(traversal, m) :+ KleeneStarTraversal(traversal))
        case (traversal,   Some(m),        Some(n)) if m == n => ConcatTraversal(repeat(traversal, n))
        case (traversal,   Some(m),        Some(n)) => ConcatTraversal(repeat(traversal, m) ++ repeat(OptionalTraversal(traversal), n - m))
      }
  }

  val concatenableTraversal: P[Traversal] = P(quantifiedTraversal | repeatedTraversal | rangedTraversal | atomicTraversal)

  val concatTraversal: P[Traversal] = P(concatenableTraversal.rep(1)).map {
    case Seq(traversal) => traversal
    case traversals => ConcatTraversal(traversals.toList)
  }

  val orTraversal: P[Traversal] = P(concatTraversal.rep(min = 1, sep = "|")).map {
    case Seq(traversal) => traversal
    case traversals => OrTraversal(traversals.toList)
  }


}
