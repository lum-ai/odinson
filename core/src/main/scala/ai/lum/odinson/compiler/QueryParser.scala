package ai.lum.odinson.compiler

import fastparse._
import ScriptWhitespace._
import ai.lum.odinson.utils.exceptions.OdinsonException

class QueryParser(
  val allTokenFields: Seq[String], // the names of all valid token fields
  val defaultTokenField: String // the name of the default token field
) {

  import QueryParser._

  // parser's entry point
  def parseBasicQuery(query: String) = parse(query.trim, basicPattern(_)).get.value

  // FIXME temporary entrypoint
  def parseEventQuery(query: String) =
    parse(query.trim, eventPattern(_), verboseFailures = true).get.value

  def eventPattern[_: P]: P[Ast.EventPattern] = {
    P(Start ~ "trigger" ~ "=" ~ surfacePattern ~ argumentPattern.rep(1) ~ End).map {
      case (trigger, arguments) => Ast.EventPattern(trigger, arguments.toList)
    }
  }

  def argumentPattern[_: P]: P[Ast.ArgumentPattern] = {
    P(existingArgumentPattern | promotedArgumentPattern | untypedArgumentPattern)
  }

  // the argument must be a mention that already exists in the state
  def existingArgumentPattern[_: P]: P[Ast.ArgumentPattern] = {
    P(existingArgumentPatternWithFullTraversal | existingArgumentPatternWithoutFullTraversal)
  }

  // the argument must be a mention that already exists in the state
  def existingArgumentPatternWithFullTraversal[_: P]: P[Ast.ArgumentPattern] = {
    P(Literals.identifier.! ~ ":" ~ Literals.identifier.! ~
      quantifier(includeLazy = false).? ~ "=" ~
      fullTraversalSurface ~ disjunctiveTraversal.?).map {
      case (name, label, quant, traversalsWithSurface, lastTraversal) =>
        // the kind of mention we want
        val mention = Ast.MentionPattern(None, label)
        // if the end of the argument pattern is a surface pattern
        // then we want to use it to constrain the retrieved mention
        val fullTraversal = lastTraversal match {
          case Some(t) =>
            val lastStep = Ast.SingleStepFullTraversalPattern(t, mention)
            Ast.ConcatFullTraversalPattern(List(traversalsWithSurface, lastStep))
          case None =>
            traversalsWithSurface.addMentionFilterToTerminals(mention, allowPromotion = false)
        }
        // get quantifier parameters
        val (min, max) = quant match {
          case Some(GreedyQuantifier(min, max)) => (min, max)
          case _                                => (1, Some(1))
        }
        Ast.ArgumentPattern(name, Some(label), fullTraversal, min, max, promote = false)
    }
  }

  // the argument must be a mention that already exists in the state
  def existingArgumentPatternWithoutFullTraversal[_: P]: P[Ast.ArgumentPattern] = {
    P(Literals.identifier.! ~ ":" ~ Literals.identifier.! ~
      quantifier(includeLazy = false).? ~ "=" ~ disjunctiveTraversal).map {
      case (name, label, quant, lastTraversal) =>
        // the kind of mention we want
        val mention = Ast.MentionPattern(None, label)
        // if the end of the argument pattern is a surface pattern
        // then we want to use it to constrain the retrieved mention
        val fullTraversal = Ast.SingleStepFullTraversalPattern(lastTraversal, mention)
        // get quantifier parameters
        val (min, max) = quant match {
          case Some(GreedyQuantifier(min, max)) => (min, max)
          case _                                => (1, Some(1))
        }
        Ast.ArgumentPattern(name, Some(label), fullTraversal, min, max, promote = false)
    }
  }

  // the argument will be promoted to a mention if it isn't one already
  def promotedArgumentPattern[_: P]: P[Ast.ArgumentPattern] = {
    P(promotedArgumentPatternWithFullTraversal | promotedArgumentPatternWithoutFullTraversal)
  }

  // the argument will be promoted to a mention if it isn't one already
  def promotedArgumentPatternWithFullTraversal[_: P]: P[Ast.ArgumentPattern] = {
    P(Literals.identifier.! ~ ":" ~ "^" ~ Literals.identifier.! ~
      quantifier(includeLazy = false).? ~ "=" ~
      fullTraversalSurface ~ disjunctiveTraversal.?).map {
      case (name, label, quant, traversalsWithSurface, lastTraversal) =>
        // the kind of mention we want
        val mention = Ast.MentionPattern(None, label)
        val fullTraversal = lastTraversal match {
          case Some(t) =>
            // if we don't have a final token pattern then assume a wildcard
            val wildcard = Ast.ConstraintPattern(Ast.Wildcard)
            val mentionOrWildcard = Ast.DisjunctivePattern(List(mention, wildcard))
            val lastStep = Ast.SingleStepFullTraversalPattern(t, mentionOrWildcard)
            Ast.ConcatFullTraversalPattern(List(traversalsWithSurface, lastStep))
          case None =>
            traversalsWithSurface.addMentionFilterToTerminals(mention, allowPromotion = true)
        }
        // get quantifier parameters
        val (min, max) = quant match {
          case Some(GreedyQuantifier(min, max)) => (min, max)
          case _                                => (1, Some(1))
        }
        Ast.ArgumentPattern(name, Some(label), fullTraversal, min, max, promote = true)
    }
  }

  // the argument will be promoted to a mention if it isn't one already
  def promotedArgumentPatternWithoutFullTraversal[_: P]: P[Ast.ArgumentPattern] = {
    P(Literals.identifier.! ~ ":" ~ "^" ~ Literals.identifier.! ~
      quantifier(includeLazy = false).? ~ "=" ~ disjunctiveTraversal).map {
      case (name, label, quant, lastTraversal) =>
        // the kind of mention we want
        val mention = Ast.MentionPattern(None, label)
        // if we don't have a final token pattern then assume a wildcard
        val wildcard = Ast.ConstraintPattern(Ast.Wildcard)
        val mentionOrWildcard = Ast.DisjunctivePattern(List(mention, wildcard))
        val fullTraversal = Ast.SingleStepFullTraversalPattern(lastTraversal, mentionOrWildcard)
        // get quantifier parameters
        val (min, max) = quant match {
          case Some(GreedyQuantifier(min, max)) => (min, max)
          case _                                => (1, Some(1))
        }
        Ast.ArgumentPattern(name, Some(label), fullTraversal, min, max, promote = true)
    }
  }

  // the argument has no declared type
  def untypedArgumentPattern[_: P]: P[Ast.ArgumentPattern] = {
    P(untypedArgumentPatternWithFullTraversal | untypedArgumentPatternWithoutFullTraversal)
  }

  // this production handles arguments without label, with full traversal, and with optional half step
  def untypedArgumentPatternWithFullTraversal[_: P]: P[Ast.ArgumentPattern] = {
    P(Literals.identifier.! ~ quantifier(includeLazy =
      false).? ~ "=" ~ fullTraversalSurface ~ disjunctiveTraversal.?).map {
      case (name, quant, traversalsWithSurface, lastTraversal) =>
        val fullTraversal = lastTraversal match {
          case None    => traversalsWithSurface
          case Some(t) =>
            // if we don't have a final token pattern then assume a wildcard
            val wildcard = Ast.ConstraintPattern(Ast.Wildcard)
            val lastStep = Ast.SingleStepFullTraversalPattern(t, wildcard)
            Ast.ConcatFullTraversalPattern(List(traversalsWithSurface, lastStep))
        }
        // get quantifier parameters
        val (min, max) = quant match {
          case Some(GreedyQuantifier(min, max)) => (min, max)
          case _                                => (1, Some(1))
        }
        Ast.ArgumentPattern(name, None, fullTraversal, min, max, promote = true)
    }
  }

  // this production handles arguments without label and with a single half step
  def untypedArgumentPatternWithoutFullTraversal[_: P]: P[Ast.ArgumentPattern] = {
    P(Literals.identifier.! ~ quantifier(includeLazy = false).? ~ "=" ~ disjunctiveTraversal).map {
      case (name, quant, lastTraversal) =>
        // if we don't have a final token pattern then assume a wildcard
        val wildcard = Ast.ConstraintPattern(Ast.Wildcard)
        val fullTraversal = Ast.SingleStepFullTraversalPattern(lastTraversal, wildcard)
        // get quantifier parameters
        val (min, max) = quant match {
          case Some(GreedyQuantifier(min, max)) => (min, max)
          case _                                => (1, Some(1))
        }
        Ast.ArgumentPattern(name, None, fullTraversal, min, max, promote = true)
    }
  }

  // grammar's top-level symbol
  def basicPattern[_: P]: P[Ast.Pattern] = {
    P(Start ~ graphTraversalPattern ~ End)
  }

  def graphTraversalPattern[_: P]: P[Ast.Pattern] = {
    P(surfacePattern ~ fullTraversalSurface.?).map {
      case (src, None)            => src
      case (src, Some(traversal)) => Ast.GraphTraversalPattern(src, traversal)
    }
  }

  //////////////////////////
  //
  // sequence of (GraphTraversal, SurfacePattern) pairs
  //
  //////////////////////////

  def fullTraversalSurface[_: P]: P[Ast.FullTraversalPattern] = {
    P(atomicTraversalSurface.rep(1)).map {
      case Seq(t) => t
      case ts     => Ast.ConcatFullTraversalPattern(ts.toList)
    }
  }

  def repeatedTraversalSurface[_: P]: P[Ast.FullTraversalPattern] = {
    P("(" ~ fullTraversalSurface ~ ")" ~ quantifier(includeLazy = false).?).map {
      case (t, None) => t
      case (t, Some(GreedyQuantifier(min, maxOption))) =>
        Ast.RepeatFullTraversalPattern(min, maxOption.getOrElse(Int.MaxValue), t)
      case _ => ???
    }
  }

  def atomicTraversalSurface[_: P]: P[Ast.FullTraversalPattern] = {
    P(singleTraversalSurface | repeatedTraversalSurface)
  }

  def singleTraversalSurface[_: P]: P[Ast.SingleStepFullTraversalPattern] = {
    P(disjunctiveTraversal ~ surfacePattern).map {
      case (tr, surf) => Ast.SingleStepFullTraversalPattern(tr, surf)
    }
  }

  //////////////////////////
  //
  // surface patterns
  //
  //////////////////////////

  def surfacePattern[_: P]: P[Ast.Pattern] = {
    P(disjunctivePattern)
  }

  def disjunctivePattern[_: P]: P[Ast.Pattern] = {
    P(concatenatedPattern.rep(min = 1, sep = "|")).map {
      case Seq(pattern) => pattern
      case patterns     => Ast.DisjunctivePattern(patterns.toList)
    }
  }

  def concatenatedPattern[_: P]: P[Ast.Pattern] = {
    P(quantifiedPattern.rep(1)).map {
      case Seq(pattern) => pattern
      case patterns     => Ast.ConcatenatedPattern(patterns.toList)
    }
  }

  def quantifiedPattern[_: P]: P[Ast.Pattern] = {
    P(atomicPattern ~ quantifier(includeLazy = true).?).map {
      case (pattern, None) => pattern
      case (pattern, Some(GreedyQuantifier(min, max))) =>
        Ast.GreedyRepetitionPattern(pattern, min, max)
      case (pattern, Some(LazyQuantifier(min, max))) => Ast.LazyRepetitionPattern(pattern, min, max)
    }
  }

  def atomicPattern[_: P]: P[Ast.Pattern] = {
    P(
      constraintPattern | mentionPattern | "(" ~ disjunctivePattern ~ ")" | expandPattern | namedCapturePattern | assertionPattern
    )
  }

  def mentionPattern[_: P]: P[Ast.Pattern] = {
    P("@" ~ Literals.string).map(label => Ast.MentionPattern(None, label))
  }

  def namedCapturePattern[_: P]: P[Ast.Pattern] = {
    P(
      "(?<" ~ Literals.identifier.! ~ (":" ~ Literals.identifier.!).? ~ ">" ~ disjunctivePattern ~ ")"
    ).map {
      case (name, maybeLabel, pattern) =>
        Ast.NamedCapturePattern(name, maybeLabel, pattern)
    }
  }

  def expandPattern[_: P]: P[Ast.Pattern] = {
    P("(?^" ~ graphTraversalPattern ~ ")").map {
      case pattern => Ast.ExpandPattern(pattern)
    }
  }

  def constraintPattern[_: P]: P[Ast.Pattern] = {
    P(tokenConstraint).map(Ast.ConstraintPattern)
  }

  def assertionPattern[_: P]: P[Ast.Pattern] = {
    P(sentenceStart | sentenceEnd | lookaround).map(Ast.AssertionPattern)
  }

  ///////////////////////////
  //
  // quantifiers
  //
  ///////////////////////////

  def quantifier[_: P](includeLazy: Boolean): P[Quantifier] = {
    P(quantOperator(includeLazy) | range(includeLazy) | repetition)
  }

  def quantOperator[_: P](includeLazy: Boolean): P[Quantifier] = {
    if (includeLazy) {
      P(StringIn("?", "*", "+", "??", "*?", "+?")).!.map {
        // format: off
        case "?"  => GreedyQuantifier(0, Some(1))
        case "*"  => GreedyQuantifier(0, None)
        case "+"  => GreedyQuantifier(1, None)
        case "??" => LazyQuantifier(0, Some(1))
        case "*?" => LazyQuantifier(0, None)
        case "+?" => LazyQuantifier(1, None)
        case _ => ??? // this shouldn't happen
        // format: on
      }
    } else {
      P(StringIn("?", "*", "+")).!.map {
        // format: off
        case "?"  => GreedyQuantifier(0, Some(1))
        case "*"  => GreedyQuantifier(0, None)
        case "+"  => GreedyQuantifier(1, None)
        case _ => ??? // this shouldn't happen
        // format: on
      }
    }
  }

  def range[_: P](includeLazy: Boolean): P[Quantifier] = {
    if (includeLazy) {
      P("{" ~ Literals.unsignedInt.? ~ "," ~ Literals.unsignedInt.? ~ StringIn(
        "}",
        "}?"
      ).!).flatMap {
        // format: off
        case (Some(min), Some(max), _) if min > max => Fail
        case (None,      maxOption, "}")  => Pass(GreedyQuantifier(  0, maxOption))
        case (Some(min), maxOption, "}")  => Pass(GreedyQuantifier(min, maxOption))
        case (None,      maxOption, "}?") => Pass(LazyQuantifier  (  0, maxOption))
        case (Some(min), maxOption, "}?") => Pass(LazyQuantifier  (min, maxOption))
        // format: on
      }
    } else {
      P("{" ~ Literals.unsignedInt.? ~ "," ~ Literals.unsignedInt.? ~ "}").flatMap {
        case (Some(min), Some(max)) if min > max => Fail
        case (None, maxOption)                   => Pass(GreedyQuantifier(0, maxOption))
        case (Some(min), maxOption)              => Pass(GreedyQuantifier(min, maxOption))
      }
    }
  }

  def repetition[_: P]: P[Quantifier] = {
    P("{" ~ Literals.unsignedInt ~ "}").map {
      case n => GreedyQuantifier(n, Some(n))
    }
  }

  ///////////////////////////
  //
  // assertions
  //
  ///////////////////////////

  def sentenceStart[_: P]: P[Ast.Assertion] = {
    P("<s>").map(_ => Ast.SentenceStartAssertion)
  }

  def sentenceEnd[_: P]: P[Ast.Assertion] = {
    P("</s>").map(_ => Ast.SentenceEndAssertion)
  }

  def lookaround[_: P]: P[Ast.Assertion] = {
    P(StringIn("(?=", "(?!", "(?<=", "(?<!").! ~ disjunctivePattern ~ ")").map {
      // format: off
      case ("(?=",  pattern) => Ast.PositiveLookaheadAssertion(pattern)
      case ("(?!",  pattern) => Ast.NegativeLookaheadAssertion(pattern)
      case ("(?<=", pattern) => Ast.PositiveLookbehindAssertion(pattern)
      case ("(?<!", pattern) => Ast.NegativeLookbehindAssertion(pattern)
      // format: on
      case _ => ???
    }
  }

  ///////////////////////////
  //
  // graph traversals
  //
  ///////////////////////////

  def disjunctiveTraversal[_: P]: P[Ast.Traversal] = {
    P(concatenatedTraversal.rep(min = 1, sep = "|")).map {
      case Seq(traversal) => traversal
      case traversals     => Ast.DisjunctiveTraversal(traversals.toList)
    }
  }

  def concatenatedTraversal[_: P]: P[Ast.Traversal] = {
    P(quantifiedTraversal.rep(1)).map {
      case Seq(traversal) => traversal
      case traversals     => Ast.ConcatenatedTraversal(traversals.toList)
    }
  }

  def quantifiedTraversal[_: P]: P[Ast.Traversal] = {
    P(atomicTraversal ~ quantifier(includeLazy = false).?).map {
      case (traversal, None) =>
        traversal
      case (traversal, Some(GreedyQuantifier(1, Some(1)))) =>
        traversal
      case (_, Some(GreedyQuantifier(0, Some(0)))) =>
        Ast.NoTraversal
      case (traversal, Some(GreedyQuantifier(0, Some(1)))) =>
        Ast.OptionalTraversal(traversal)
      case (traversal, Some(GreedyQuantifier(0, None))) =>
        Ast.KleeneStarTraversal(traversal)
      case (traversal, Some(GreedyQuantifier(n, None))) =>
        val clauses = List.fill(n)(traversal) :+ Ast.KleeneStarTraversal(traversal)
        Ast.ConcatenatedTraversal(clauses)
      case (traversal, Some(GreedyQuantifier(m, Some(n)))) if m == n =>
        val clauses = List.fill(n)(traversal)
        Ast.ConcatenatedTraversal(clauses)
      case (traversal, Some(GreedyQuantifier(m, Some(n)))) if m < n =>
        val required = List.fill(m)(traversal)
        val optional = List.fill(n - m)(Ast.OptionalTraversal(traversal))
        Ast.ConcatenatedTraversal(required ++ optional)
    }
  }

  def atomicTraversal[_: P]: P[Ast.Traversal] = {
    P(singleStepTraversal | "(" ~ disjunctiveTraversal ~ ")")
  }

  def singleStepTraversal[_: P]: P[Ast.Traversal] = {
    P(outgoingTraversal | incomingTraversal | outgoingWildcard | incomingWildcard)
  }

  def incomingWildcard[_: P]: P[Ast.Traversal] = {
    P("<<").map(_ => Ast.IncomingWildcard)
  }

  def incomingTraversal[_: P]: P[Ast.Traversal] = {
    P("<" ~ anyMatcher).map(Ast.IncomingTraversal)
  }

  def outgoingWildcard[_: P]: P[Ast.Traversal] = {
    P(">>").map(_ => Ast.OutgoingWildcard)
  }

  def outgoingTraversal[_: P]: P[Ast.Traversal] = {
    P(">" ~ anyMatcher).map(Ast.OutgoingTraversal)
  }

  ///////////////////////////
  //
  // token constraints
  //
  ///////////////////////////

  def tokenConstraint[_: P]: P[Ast.Constraint] = {
    P(explicitConstraint | defaultFieldConstraint)
  }

  def defaultFieldConstraint[_: P]: P[Ast.Constraint] = {
    P(defaultFieldRegexConstraint | defaultFieldStringConstraint)
  }

  def defaultFieldStringConstraint[_: P]: P[Ast.Constraint] = {
    // a negative lookahead is required to ensure that this constraint
    // is not followed by a colon or an equals, or else it is an argument name
    P(Literals.string ~ !(":" | quantifier(includeLazy = false).? ~ "=") ~ "~".!.?).map {
      case (string, None) =>
        Ast.FieldConstraint(defaultTokenField, Ast.StringMatcher(string))
      case (string, Some(_)) =>
        Ast.FuzzyConstraint(defaultTokenField, Ast.StringMatcher(string))
    }
  }

  def defaultFieldRegexConstraint[_: P]: P[Ast.Constraint] = {
    P(Literals.regex).map { regex =>
      Ast.FieldConstraint(defaultTokenField, Ast.RegexMatcher(regex))
    }
  }

  def explicitConstraint[_: P]: P[Ast.Constraint] = {
    P("[" ~ disjunctiveConstraint.? ~ "]").map {
      case None             => Ast.Wildcard
      case Some(constraint) => constraint
    }
  }

  def disjunctiveConstraint[_: P]: P[Ast.Constraint] = {
    P(conjunctiveConstraint.rep(min = 1, sep = "|")).map {
      case Seq(constraint) => constraint
      case constraints     => Ast.DisjunctiveConstraint(constraints.toList)
    }
  }

  def conjunctiveConstraint[_: P]: P[Ast.Constraint] = {
    P(negatedConstraint.rep(min = 1, sep = "&")).map {
      case Seq(constraint) => constraint
      case constraints     => Ast.ConjunctiveConstraint(constraints.toList)
    }
  }

  def negatedConstraint[_: P]: P[Ast.Constraint] = {
    P("!".!.? ~ atomicConstraint).map {
      case (None, constraint)    => constraint
      case (Some(_), constraint) => Ast.NegatedConstraint(constraint)
    }
  }

  def atomicConstraint[_: P]: P[Ast.Constraint] = {
    P(fieldConstraint | "(" ~ disjunctiveConstraint ~ ")")
  }

  def fieldConstraint[_: P]: P[Ast.Constraint] = {
    P(regexFieldConstraint | stringFieldConstraint)
  }

  def regexFieldConstraint[_: P]: P[Ast.Constraint] = {
    P(fieldName ~ StringIn("=", "!=").! ~ regexMatcher).map {
      // format: off
      case (name, "=",  matcher) => Ast.FieldConstraint(name, matcher)
      case (name, "!=", matcher) => Ast.NegatedConstraint(Ast.FieldConstraint(name, matcher))
      // format: on
      case _ => ??? // this shouldn't happen
    }
  }

  def stringFieldConstraint[_: P]: P[Ast.Constraint] = {
    P(fieldName ~ StringIn("=", "!=").! ~ extendedStringMatcher ~ "~".!.?).map {
      // format: off
      case (name, "=",  matcher, None)    => Ast.FieldConstraint(name, matcher)
      case (name, "!=", matcher, None)    => Ast.NegatedConstraint(Ast.FieldConstraint(name, matcher))
      case (name, "=",  matcher, Some(_)) => Ast.FuzzyConstraint(name, matcher)
      case (name, "!=", matcher, Some(_)) => Ast.NegatedConstraint(Ast.FuzzyConstraint(name, matcher))
      // format: on
      case _ => ??? // this shouldn't happen
    }
  }

  // any value in `allTokenFields` is a valid field name
  def fieldName[_: P]: P[String] = {
    P(Literals.identifier).flatMap { identifier =>
      if (allTokenFields contains identifier) Pass(identifier) else Fail
    }
  }

  def anyMatcher[_: P]: P[Ast.Matcher] = {
    P(extendedStringMatcher | regexMatcher)
  }

  def extendedStringMatcher[_: P]: P[Ast.StringMatcher] = {
    P(Literals.extendedString.map(Ast.StringMatcher))
  }

  def stringMatcher[_: P]: P[Ast.StringMatcher] = {
    P(Literals.string.map(Ast.StringMatcher))
  }

  def regexMatcher[_: P]: P[Ast.RegexMatcher] = {
    P(Literals.regex.map(Ast.RegexMatcher))
  }

}

object QueryParser {

  sealed trait Quantifier
  case class GreedyQuantifier(min: Int, max: Option[Int]) extends Quantifier
  case class LazyQuantifier(min: Int, max: Option[Int]) extends Quantifier

}
