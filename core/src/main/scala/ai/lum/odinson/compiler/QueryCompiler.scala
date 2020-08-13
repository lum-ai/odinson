package ai.lum.odinson.compiler

import java.io.File
import scala.collection.mutable.ArrayBuffer
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.join._
import org.apache.lucene.search.spans._
import org.apache.lucene.queryparser.classic.{ QueryParser => LuceneQueryParser }
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import com.typesafe.config.Config
import ai.lum.common.StringUtils._
import ai.lum.common.ConfigUtils._
import ai.lum.common.ConfigFactory
import ai.lum.odinson.lucene.search._
import ai.lum.odinson.lucene.search.spans._
import ai.lum.odinson.digraph._
import ai.lum.odinson.state.State

class QueryCompiler(
    val allTokenFields: Seq[String],
    val defaultTokenField: String,
    val sentenceLengthField: String,
    val dependenciesField: String,
    val incomingTokenField: String,
    val outgoingTokenField: String,
    val dependenciesVocabulary: Vocabulary,
    val aggressiveNormalizationToDefaultField: Boolean
) {

  val parser = new QueryParser(allTokenFields, defaultTokenField)

  /** query parser for parent doc queries */
  val queryParser = new LuceneQueryParser("docId", new WhitespaceAnalyzer)

  // FIXME temporary entrypoint
  def compileEventQuery(pattern: String): OdinsonQuery = {
    val ast = parser.parseEventQuery(pattern)
    val query = mkOdinsonQuery(ast)
    query.getOrElse(new FailQuery(defaultTokenField))
  }

  def compile(pattern: String): OdinsonQuery = {
    val ast = parser.parseBasicQuery(pattern)
    val query = mkOdinsonQuery(ast)
    query.getOrElse(new FailQuery(defaultTokenField))
  }

  def mkQuery(pattern: String): OdinsonQuery = {
    compile(pattern)
  }

  def mkParentQuery(parentPattern: String): Query = queryParser.parse(parentPattern)

  def mkQuery(pattern: String, parentPattern: String): OdinsonQuery = {
    val query = compile(pattern)
    val parentQuery = queryParser.parse(parentPattern)
    mkQuery(query, parentQuery)
  }

  def mkQuery(pattern: String, parentQuery: Query): OdinsonQuery = {
    val query = compile(pattern)
    mkQuery(query, parentQuery)
  }

  def mkQuery(query: OdinsonQuery, parentPattern: String): OdinsonQuery = {
    val parentQuery = queryParser.parse(parentPattern)
    mkQuery(query, parentQuery)
  }

  def mkQuery(query: OdinsonQuery, parentQuery: Query): OdinsonQuery = {
    // FIXME the strings "type" and "parent" should probably be defined in the config
    val termQuery = new TermQuery(new Term("type", "parent"))
    val parentFilter = new QueryBitSetProducer(termQuery)
    val filter = new ToChildBlockJoinQuery(parentQuery, parentFilter)
    new OdinsonFilteredQuery(query, filter)
  }

  /** Constructs an OdinsonQuery from an AST. */
  def mkOdinsonQuery(ast: Ast.Pattern): Option[OdinsonQuery] = ast match {

    // filtered query

    case Ast.FilterPattern(main, filter) =>
      for {
        q <- mkOdinsonQuery(main)
        c <- mkOdinsonQuery(filter)
      } yield new OdinsonSpanContainingQuery(q, c)

    // zero-width assertions

    case Ast.AssertionPattern(Ast.SentenceStartAssertion) =>
      val q = new DocStartQuery(defaultTokenField)
      Some(q)

    case Ast.AssertionPattern(Ast.SentenceEndAssertion) =>
      val q = new DocEndQuery(defaultTokenField, sentenceLengthField)
      Some(q)

    case Ast.AssertionPattern(Ast.PositiveLookaheadAssertion(pattern)) =>
      mkOdinsonQuery(pattern).map(q => new LookaheadQuery(q))

    case Ast.AssertionPattern(Ast.PositiveLookbehindAssertion(pattern)) =>
      mkOdinsonQuery(pattern).map(q => new LookbehindQuery(q))

    case Ast.AssertionPattern(Ast.NegativeLookaheadAssertion(pattern)) =>
      val include = new AllNGramsQuery(defaultTokenField, sentenceLengthField, 0)
      val lookahead = mkOdinsonQuery(pattern).map(q => new LookaheadQuery(q))
      lookahead.map(exclude => new OdinNotQuery(include, exclude, defaultTokenField))

    case Ast.AssertionPattern(Ast.NegativeLookbehindAssertion(pattern)) =>
      val include = new AllNGramsQuery(defaultTokenField, sentenceLengthField, 0)
      val lookbehind = mkOdinsonQuery(pattern).map(q => new LookbehindQuery(q))
      lookbehind.map(exclude => new OdinNotQuery(include, exclude, defaultTokenField))

    // token constraints

    case Ast.ConstraintPattern(constraint) =>
      val q = mkConstraintQuery(constraint)
      Some(q)

    // event pattern

    case Ast.EventPattern(triggerPattern, argPatterns) =>
      val triggerOption = mkOdinsonQuery(triggerPattern)
      if (triggerOption.isEmpty) return None
      var triggerQuery = triggerOption.get
      // split arguments in required and optional
      val (required, optional) = argPatterns.partition(_.min > 0)
      val reqArgQueries = required.flatMap(mkArgumentQuery)
      val optArgQueries = optional.flatMap(mkArgumentQuery)
      // all arguments should survive the transformation
      if (reqArgQueries.length != required.length || optArgQueries.length != optional.length) {
        ???
      }
      // add start-constraints of required args to trigger
      for (arg <- reqArgQueries) {
        triggerQuery = arg.fullTraversal.firstGraphTraversal match {
          case None => triggerQuery
          case Some(tr) => addConstraint(triggerQuery, mkStartConstraint(tr))
        }
      }
      // return event query
      val q = new OdinsonEventQuery(triggerQuery, reqArgQueries, optArgQueries, dependenciesField, sentenceLengthField)
      Some(q)

    // disjunctive patterns

    case Ast.DisjunctivePattern(patterns) =>
      patterns.flatMap(mkOdinsonQuery).distinct match {
        case Seq() => None
        case Seq(q) => Some(q)
        case clauses =>
          val q = new OdinOrQuery(clauses, defaultTokenField)
          Some(q)
      }

    // pattern concatenation

    case Ast.ConcatenatedPattern(patterns) =>
      patterns.flatMap(mkOdinsonQuery) match {
        case Seq() => None
        case Seq(q) => Some(q)
        case clauses =>
          // handle consecutive wildcards and nested concatenations
          val newClauses = clauses.foldRight(List.empty[OdinsonQuery]) {
            case (c1:AllNGramsQuery, (c2:AllNGramsQuery) :: cs) =>
              // merge consecutive wildcards
              val c = new AllNGramsQuery(defaultTokenField, sentenceLengthField, c1.n + c2.n)
              c :: cs
            case (concat:OdinConcatQuery, cs) =>
              // expand nested concatenations
              (concat.clauses.last, cs.head) match {
                case (c1: AllNGramsQuery, c2: AllNGramsQuery) =>
                  // take care of consecutive wildcards when expanding
                  val c = new AllNGramsQuery(defaultTokenField, sentenceLengthField, c1.n + c2.n)
                  concat.clauses.init ::: List(c) ::: cs.tail
                case _ => concat.clauses ::: cs
              }
            case (c , cs) => c :: cs
          }
          // if collapsed into a single query then don't concatenate
          val query = newClauses match {
            case List(q) => q
            case qs => new OdinConcatQuery(qs, defaultTokenField, sentenceLengthField)
          }
          // return resulting query
          Some(query)
      }

    // named captures

    case Ast.NamedCapturePattern(name, label, pattern) =>
      mkOdinsonQuery(pattern).map(q => new OdinQueryNamedCapture(q, name, label))

    // mentions

    case Ast.MentionPattern(_, label) =>
      Some(new StateQuery(defaultTokenField, label))

    // query expansion (or flattening?)

    case Ast.ExpandPattern(pattern) =>
      mkOdinsonQuery(pattern).map(q => new ExpandQuery(q))

    // graph traversal

    case Ast.GraphTraversalPattern(src, tr) =>
      mkFullTraversalQuery(tr).flatMap { fullTraversal =>
        val srcQuery = mkOdinsonQuery(src).map { q =>
          fullTraversal.firstGraphTraversal match {
            case None => q
            case Some(t) => addConstraint(q, mkStartConstraint(t))
          }
        }
        if (srcQuery.isDefined) {
          val q = new GraphTraversalQuery(defaultTokenField, dependenciesField, sentenceLengthField, srcQuery.get, fullTraversal)
          Some(q)
        } else {
          None
        }
      }

    case Ast.LazyRepetitionPattern(pattern@_, 0, Some(0)) =>
      val q = new AllNGramsQuery(defaultTokenField, sentenceLengthField, 0)
      Some(q)

    case Ast.LazyRepetitionPattern(pattern, 0, Some(1)) =>
      mkOdinsonQuery(pattern).map {
        case q: AllNGramsQuery if q.n == 0 => q
        case q => new OdinsonOptionalQuery(q, sentenceLengthField, isGreedy = false)
      }

    case Ast.LazyRepetitionPattern(pattern, 0, None) =>
      mkOdinsonQuery(pattern).map {
        case q: AllNGramsQuery if q.n == 0 => q
        case q =>
          val oneOrMore = new OdinRepetitionQuery(q, 1, Int.MaxValue, isGreedy = false)
          new OdinsonOptionalQuery(oneOrMore, sentenceLengthField, isGreedy = false)
      }

    case Ast.LazyRepetitionPattern(pattern, 1, Some(1)) =>
      mkOdinsonQuery(pattern)

    case Ast.LazyRepetitionPattern(pattern, min, None) =>
      mkOdinsonQuery(pattern).map {
        case q: AllNGramsQuery if q.n == 0 => q
        case q => new OdinRepetitionQuery(q, min, Int.MaxValue, isGreedy = false)
      }

    case Ast.LazyRepetitionPattern(pattern, n, Some(m)) if n == m =>
      mkOdinsonQuery(pattern).map {
        case q: AllNGramsQuery if q.n == 0 => q
        case q: AllNGramsQuery => new AllNGramsQuery(defaultTokenField, sentenceLengthField, q.n * n)
        case q => new OdinRepetitionQuery(q, n, m, isGreedy = false)
      }

    case Ast.LazyRepetitionPattern(pattern, min, Some(max)) =>
      mkOdinsonQuery(pattern).map {
        case q: AllNGramsQuery if q.n == 0 => q
        case q: AllNGramsQuery =>
          val clauses = for (i <- min to max) yield {
            new AllNGramsQuery(defaultTokenField, sentenceLengthField, i * q.n)
          }
          new OdinOrQuery(clauses.toList, defaultTokenField)
        case q if min == 0 =>
          val oneOrMore = new OdinRepetitionQuery(q, 1, max, isGreedy = false)
          new OdinsonOptionalQuery(oneOrMore, sentenceLengthField, isGreedy = false)
        case q => new OdinRepetitionQuery(q, min, max, isGreedy = false)
      }

    case Ast.GreedyRepetitionPattern(pattern@_, 0, Some(0)) =>
      val q = new AllNGramsQuery(defaultTokenField, sentenceLengthField, 0)
      Some(q)

    case Ast.GreedyRepetitionPattern(pattern, 0, Some(1)) =>
      mkOdinsonQuery(pattern).map {
        case q: AllNGramsQuery if q.n == 0 => q
        case q => new OdinsonOptionalQuery(q, sentenceLengthField, isGreedy = true)
      }

    case Ast.GreedyRepetitionPattern(pattern, 0, None) =>
      mkOdinsonQuery(pattern).map {
        case q: AllNGramsQuery if q.n == 0 => q
        case q =>
          val oneOrMore = new OdinRepetitionQuery(q, 1, Int.MaxValue, isGreedy = true)
          new OdinsonOptionalQuery(oneOrMore, sentenceLengthField, isGreedy = true)
      }

    case Ast.GreedyRepetitionPattern(pattern, 1, Some(1)) =>
      mkOdinsonQuery(pattern)

    case Ast.GreedyRepetitionPattern(pattern, min, None) =>
      mkOdinsonQuery(pattern).map {
        case q: AllNGramsQuery if q.n == 0 => q
        case q => new OdinRepetitionQuery(q, min, Int.MaxValue, isGreedy = true)
      }

    case Ast.GreedyRepetitionPattern(pattern, n, Some(m)) if n == m =>
      mkOdinsonQuery(pattern).map {
        case q: AllNGramsQuery if q.n == 0 => q
        case q: AllNGramsQuery => new AllNGramsQuery(defaultTokenField, sentenceLengthField, q.n * n)
        case q => new OdinRepetitionQuery(q, n, m, isGreedy = true)
      }

    case Ast.GreedyRepetitionPattern(pattern, min, Some(max)) =>
      mkOdinsonQuery(pattern).map {
        case q: AllNGramsQuery if q.n == 0 => q
        case q: AllNGramsQuery =>
          val clauses = for (i <- max to min by -1) yield {
            new AllNGramsQuery(defaultTokenField, sentenceLengthField, i * q.n)
          }
          new OdinOrQuery(clauses.toList, defaultTokenField)
        case q if min == 0 =>
          val oneOrMore = new OdinRepetitionQuery(q, 1, max, isGreedy = true)
          new OdinsonOptionalQuery(oneOrMore, sentenceLengthField, isGreedy = true)
        case q => new OdinRepetitionQuery(q, min, max, isGreedy = true)
      }

  }

  def mkFullTraversalQuery(tr: Ast.FullTraversalPattern): Option[FullTraversalQuery] = tr match {

    case t: Ast.SingleStepFullTraversalPattern =>
      mkOdinsonQuery(t.surface).map { q =>
        val traversal = mkGraphTraversal(t.traversal)
        val surface = addConstraint(q, mkEndConstraint(traversal))
        SingleStepFullTraversalQuery(traversal, surface)
      }

    case t: Ast.RepeatFullTraversalPattern =>
      mkFullTraversalQuery(t.fullTraversal).map { fullTraversal =>
        RepetitionFullTraversalQuery(t.min, t.max, fullTraversal)
      }

    case t: Ast.ConcatFullTraversalPattern =>
      val optClauses = t.clauses.map(mkFullTraversalQuery).toArray
      val clauses = new Array[FullTraversalQuery](optClauses.length)
      var i = 0
      while (i < optClauses.length) {
        val clause = optClauses(i)
        if (clause.isEmpty) return None
        clauses(i) = clause.get
        if (i > 0) {
          for {
            c <- clause
            t <- c.firstGraphTraversal
            const = mkStartConstraint(t)
          } clauses(i - 1) = addConstraintToFullTraversal(clauses(i - 1), const)
        }
        i += 1
      }
      Some(ConcatFullTraversalQuery(clauses.toList))

  }

  def addConstraintToFullTraversal(ftq: FullTraversalQuery, c: Option[OdinsonQuery]): FullTraversalQuery = ftq match {

    case t: SingleStepFullTraversalQuery =>
      SingleStepFullTraversalQuery(t.traversal, addConstraint(t.surface, c))

    case t: RepetitionFullTraversalQuery =>
      ftq

    case t: ConcatFullTraversalQuery =>
      val lastStep = addConstraintToFullTraversal(t.fullTraversal.last, c)
      val steps = t.fullTraversal.init :+ lastStep
      ConcatFullTraversalQuery(steps)

  }

  def mkArgumentQuery(arg: Ast.ArgumentPattern): Option[ArgumentQuery] = {
    mkFullTraversalQuery(arg.fullTraversal).map { tr =>
      ArgumentQuery(arg.name, arg.label, arg.min, arg.max, tr)
    }
  }

  def addConstraint(query: OdinsonQuery, constraint: Option[OdinsonQuery]): OdinsonQuery = {
    (query, constraint) match {
      case (q, None) => q
      case (q: AllNGramsQuery, Some(c)) if q.n == 1 => c
      case (q, Some(c)) => new OdinsonSpanContainingQuery(q, c)
    }
  }

  /** Returns a Term object with its value normalized */
  def mkTerm(name: String, value: String): Term = {
    if (aggressiveNormalizationToDefaultField && name == defaultTokenField) {
      new Term(name, value.normalizeUnicodeAggressively)
    } else {
      new Term(name, value.normalizeUnicode)
    }
  }

  def mkConstraintQuery(ast: Ast.Constraint): OdinsonQuery = ast match {

    case Ast.FieldConstraint(name, Ast.StringMatcher(string)) =>
      val spanTermQuery = new SpanTermQuery(mkTerm(name, string))
      new OdinQueryWrapper(maybeMask(spanTermQuery))

    case Ast.FieldConstraint(name, Ast.RegexMatcher(regex)) =>
      val regexpQuery = new RegexpQuery(mkTerm(name, regex))
      val spanQuery = new SpanMultiTermQueryWrapper(regexpQuery)
      new OdinQueryWrapper(maybeMask(spanQuery))

    case Ast.FuzzyConstraint(name, Ast.StringMatcher(string)) =>
      val fuzzyQuery = new FuzzyQuery(mkTerm(name, string))
      val spanQuery = new SpanMultiTermQueryWrapper(fuzzyQuery)
      new OdinQueryWrapper(maybeMask(spanQuery))

    case Ast.DisjunctiveConstraint(constraints) =>
      constraints.map(mkConstraintQuery).distinct match {
        case Seq() => sys.error("OR without clauses")
        case Seq(clause) => clause
        case clauses => new OdinOrQuery(clauses, defaultTokenField)
      }

    case Ast.ConjunctiveConstraint(constraints) =>
      constraints.map(mkConstraintQuery).distinct match {
        case Seq() => sys.error("AND without clauses")
        case Seq(clause) => clause
        case clauses => new OdinTermAndQuery(clauses, defaultTokenField)
      }

    case Ast.NegatedConstraint(Ast.NegatedConstraint(constraint)) =>
      mkConstraintQuery(constraint)

    case Ast.NegatedConstraint(Ast.Wildcard) =>
      new FailQuery(defaultTokenField)

    case Ast.NegatedConstraint(constraint) =>
      val include = new AllNGramsQuery(defaultTokenField, sentenceLengthField, 1)
      val exclude = mkConstraintQuery(constraint)
      new OdinNotQuery(include, exclude, defaultTokenField)

    case Ast.Wildcard =>
      new AllNGramsQuery(defaultTokenField, sentenceLengthField, 1)

  }

  /** mask query if its field doesn't match `defaultTokenField` */
  def maybeMask(query: SpanQuery): SpanQuery = {
    if (query.getField() == defaultTokenField) query
    else new FieldMaskingSpanQuery(query, defaultTokenField)
  }

  def mkGraphTraversal(ast: Ast.Traversal): GraphTraversal = ast match {

    case Ast.NoTraversal => NoTraversal

    case Ast.IncomingWildcard => IncomingWildcard
    case Ast.OutgoingWildcard => OutgoingWildcard

    case Ast.IncomingTraversal(matcher) => mkLabelMatcher(matcher) match {
      case FailLabelMatcher => FailTraversal
      case m => Incoming(m)
    }

    case Ast.OutgoingTraversal(matcher) => mkLabelMatcher(matcher) match {
      case FailLabelMatcher => FailTraversal
      case m => Outgoing(m)
    }

    case Ast.DisjunctiveTraversal(traversals) =>
      traversals.map(mkGraphTraversal).distinct.partition(_ == NoTraversal) match {
        case (Seq(), Seq()) => sys.error("OR without clauses")
        case (Seq(), gts) =>
          gts.filter(_ != FailTraversal) match {
            case Seq() => FailTraversal
            case Seq(gt) => gt
            case gts => Union(gts)
          }
        case (nogts@_, gts) =>
          gts.filter(_ != FailTraversal) match {
            case Seq() => NoTraversal
            case Seq(gt) => Optional(gt)
            case gts => Optional(Union(gts))
          }
      }

    case Ast.ConcatenatedTraversal(traversals) =>
      traversals.map(mkGraphTraversal).filter(_ != NoTraversal) match {
        case Seq() => NoTraversal
        case Seq(gt) => gt
        case gts if gts contains FailTraversal => FailTraversal
        case gts => Concatenation(gts)
      }

    case Ast.OptionalTraversal(traversal) =>
      mkGraphTraversal(traversal) match {
        case NoTraversal => NoTraversal
        case FailTraversal => NoTraversal
        case gt => Optional(gt)
      }

    case Ast.KleeneStarTraversal(traversal) =>
      mkGraphTraversal(traversal) match {
        case NoTraversal => NoTraversal
        case FailTraversal => NoTraversal
        case gt => KleeneStar(gt)
      }

  }

  def mkLabelMatcher(m: Ast.Matcher): LabelMatcher = m match {
    case Ast.RegexMatcher(s) =>
      new RegexLabelMatcher(s.normalizeUnicode.r, dependenciesVocabulary)
    case Ast.StringMatcher(s) =>
      dependenciesVocabulary.getId(s.normalizeUnicode) match {
        case Some(id) => new ExactLabelMatcher(s, id)
        case None => FailLabelMatcher
      }
  }

  // makes a constraint for the start of a graph traversal
  def mkStartConstraint(gt: GraphTraversal): Option[OdinsonQuery] = gt match {
    case NoTraversal => None
    case FailTraversal => Some(new FailQuery(defaultTokenField))
    case IncomingWildcard => None
    case OutgoingWildcard => None
    case Incoming(FailLabelMatcher) => Some(new FailQuery(defaultTokenField))
    case Incoming(matcher: ExactLabelMatcher) =>
      val spanTermQuery = new SpanTermQuery(new Term(incomingTokenField, matcher.string))
      val odinQuery = new OdinQueryWrapper(maybeMask(spanTermQuery))
      Some(odinQuery)
    case Incoming(matcher: RegexLabelMatcher) =>
      val regexpQuery = new RegexpQuery(new Term(incomingTokenField, matcher.regex.regex))
      val spanQuery = new SpanMultiTermQueryWrapper(regexpQuery)
      val odinQuery = new OdinQueryWrapper(maybeMask(spanQuery))
      Some(odinQuery)
    case Outgoing(FailLabelMatcher) => Some(new FailQuery(defaultTokenField))
    case Outgoing(matcher: ExactLabelMatcher) =>
      val spanTermQuery = new SpanTermQuery(new Term(outgoingTokenField, matcher.string))
      val odinQuery = new OdinQueryWrapper(maybeMask(spanTermQuery))
      Some(odinQuery)
    case Outgoing(matcher: RegexLabelMatcher) =>
      val regexpQuery = new RegexpQuery(new Term(outgoingTokenField, matcher.regex.regex))
      val spanQuery = new SpanMultiTermQueryWrapper(regexpQuery)
      val odinQuery = new OdinQueryWrapper(maybeMask(spanQuery))
      Some(odinQuery)
    case Concatenation(traversals) => mkStartConstraint(traversals.head)
    case Union(traversals) =>
      traversals.map(mkStartConstraint).flatten.distinct match {
        case Seq() => None
        case Seq(clause) => Some(clause)
        case clauses => Some(new OdinOrQuery(clauses, defaultTokenField))
      }
    case Optional(traversal@_) => None
    case KleeneStar(traversals@_) => None
  }

  // makes a constraint for the end of a graph traversal
  def mkEndConstraint(gt: GraphTraversal): Option[OdinsonQuery] = gt match {
    case NoTraversal => None
    case FailTraversal => Some(new FailQuery(defaultTokenField))
    case IncomingWildcard => None
    case OutgoingWildcard => None
    case Incoming(FailLabelMatcher) => Some(new FailQuery(defaultTokenField))
    case Incoming(matcher: ExactLabelMatcher) =>
      val spanTermQuery = new SpanTermQuery(new Term(outgoingTokenField, matcher.string))
      val odinQuery = new OdinQueryWrapper(maybeMask(spanTermQuery))
      Some(odinQuery)
    case Incoming(matcher: RegexLabelMatcher) =>
      val regexpQuery = new RegexpQuery(new Term(outgoingTokenField, matcher.regex.regex))
      val spanQuery = new SpanMultiTermQueryWrapper(regexpQuery)
      val odinQuery = new OdinQueryWrapper(maybeMask(spanQuery))
      Some(odinQuery)
    case Outgoing(FailLabelMatcher) => Some(new FailQuery(defaultTokenField))
    case Outgoing(matcher: ExactLabelMatcher) =>
      val spanTermQuery = new SpanTermQuery(new Term(incomingTokenField, matcher.string))
      val odinQuery = new OdinQueryWrapper(maybeMask(spanTermQuery))
      Some(odinQuery)
    case Outgoing(matcher: RegexLabelMatcher) =>
      val regexpQuery = new RegexpQuery(new Term(incomingTokenField, matcher.regex.regex))
      val spanQuery = new SpanMultiTermQueryWrapper(regexpQuery)
      val odinQuery = new OdinQueryWrapper(maybeMask(spanQuery))
      Some(odinQuery)
    case Concatenation(traversals) => mkEndConstraint(traversals.last)
    case Union(traversals) =>
      traversals.map(mkEndConstraint).flatten.distinct match {
        case Seq() => None
        case Seq(clause) => Some(clause)
        case clauses => Some(new OdinOrQuery(clauses, defaultTokenField))
      }
    case Optional(traversal@_) => None
    case KleeneStar(traversals@_) => None
  }

}

object QueryCompiler {

  def apply(config: Config, vocabulary: Vocabulary): QueryCompiler = {
    new QueryCompiler(
      config[List[String]]("compiler.allTokenFields"),
      config[String]("compiler.defaultTokenField"),
      config[String]("compiler.sentenceLengthField"),
      config[String]("compiler.dependenciesField"),
      config[String]("compiler.incomingTokenField"),
      config[String]("compiler.outgoingTokenField"),
      vocabulary,
      config[Boolean]("compiler.aggressiveNormalizationToDefaultField")
    )
  }

}
