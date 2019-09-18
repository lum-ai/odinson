package ai.lum.odinson.compiler

import java.io.File
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.join._
import org.apache.lucene.search.spans._
import org.apache.lucene.queryparser.classic.{ QueryParser => LuceneQueryParser }
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import com.typesafe.config.Config
import ai.lum.common.ConfigUtils._
import ai.lum.odinson.lucene.search._
import ai.lum.odinson.lucene.search.spans._
import ai.lum.odinson.digraph._
import ai.lum.odinson.state.State
import ai.lum.odinson.utils.ConfigFactory

class QueryCompiler(
    val allTokenFields: Seq[String],
    val defaultTokenField: String,
    val sentenceLengthField: String,
    val dependenciesField: String,
    val incomingTokenField: String,
    val outgoingTokenField: String,
    val dependenciesVocabulary: Vocabulary,
    val normalizeQueriesToDefaultField: Boolean
) {

  val parser = new QueryParser(allTokenFields, defaultTokenField, normalizeQueriesToDefaultField)

  /** query parser for parent doc queries */
  val queryParser = new LuceneQueryParser("docId", new WhitespaceAnalyzer)

  private var state: Option[State] = None

  def setState(s: State): Unit = {
    state = Some(s)
  }

  def compile(pattern: String): OdinsonQuery = {
    val ast = parser.parseQuery(pattern)
    val query = mkOdinsonQuery(ast)
    query.getOrElse(new FailQuery(defaultTokenField))
  }

  def mkQuery(pattern: String): OdinsonQuery = {
    compile(pattern)
  }

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

    // zero-width assertions

    case Ast.AssertionPattern(Ast.SentenceStartAssertion) =>
      val q = new DocStartQuery(defaultTokenField)
      Some(q)

    case Ast.AssertionPattern(Ast.SentenceEndAssertion) =>
      val q = new DocEndQuery(defaultTokenField, sentenceLengthField)
      Some(q)

    // TODO lookarounds
    case Ast.AssertionPattern(Ast.PositiveLookaheadAssertion(pattern)) => ???
    case Ast.AssertionPattern(Ast.NegativeLookaheadAssertion(pattern)) => ???
    case Ast.AssertionPattern(Ast.PositiveLookbehindAssertion(pattern)) => ???
    case Ast.AssertionPattern(Ast.NegativeLookbehindAssertion(pattern)) => ???


    // token constraints

    case Ast.ConstraintPattern(constraint) =>
      val q = mkConstraintQuery(constraint)
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
          val q = new OdinConcatQuery(clauses, defaultTokenField, sentenceLengthField)
          Some(q)
      }

    // named captures

    case Ast.NamedCapturePattern(name, pattern) =>
      mkOdinsonQuery(pattern).map(q => new OdinQueryNamedCapture(q, name))

    // mentions

    case Ast.MentionPattern(_, label) =>
      state match {
        case None => Some(new FailQuery(defaultTokenField))
        case Some(state) => Some(new StateQuery(defaultTokenField, label, state))
      }

    // graph traversal

    case Ast.GraphTraversalPattern(src, tr, dst) =>
      mkGraphTraversal(tr) match {
        case NoTraversal => mkOdinsonQuery(src)
        case FailTraversal => None
        case traversal =>
          val srcQuery = mkOdinsonQuery(src).map(q => addConstraint(q, mkStartConstraint(traversal)))
          val dstQuery = mkOdinsonQuery(dst).map(q => addConstraint(q, mkEndConstraint(traversal)))
          if (srcQuery.isDefined && dstQuery.isDefined) {
            val q = new GraphTraversalQuery(defaultTokenField, dependenciesField, srcQuery.get, traversal, dstQuery.get)
            Some(q)
          } else {
            None
          }
      }

    case Ast.LazyRepetitionPattern(_, _, _) => ???

    case Ast.GreedyRepetitionPattern(pattern@_, 0, Some(0)) =>
      val q = new AllNGramsQuery(defaultTokenField, sentenceLengthField, 0)
      Some(q)

    case Ast.GreedyRepetitionPattern(pattern, 0, Some(1)) =>
      mkOdinsonQuery(pattern).map {
        case q: AllNGramsQuery if q.n == 0 => q
        case one =>
          val zero = new AllNGramsQuery(defaultTokenField, sentenceLengthField, 0)
          // If there is a zero-width query, it should be the first clause.
          // This is a convention we follow to be able to identify optional clauses easily.
          val clauses = List(zero, one)
          new OdinOrQuery(clauses, defaultTokenField)
      }

    case Ast.GreedyRepetitionPattern(pattern, 0, None) =>
      mkOdinsonQuery(pattern).map {
        case q: AllNGramsQuery if q.n == 0 => q
        case q =>
          val zero = new AllNGramsQuery(defaultTokenField, sentenceLengthField, 0)
          val oneOrMore = new OdinRangeQuery(q, 1, Int.MaxValue)
          // If there is a zero-width query, it should be the first clause.
          // This is a convention we follow to be able to identify optional clauses easily.
          val clauses = List(zero, oneOrMore)
          new OdinOrQuery(clauses, defaultTokenField)
      }

    case Ast.GreedyRepetitionPattern(pattern, 0, Some(max)) =>
      mkOdinsonQuery(pattern).map {
        case q: AllNGramsQuery if q.n == 0 => q
        case q =>
          val zero = new AllNGramsQuery(defaultTokenField, sentenceLengthField, 0)
          val oneOrMore = new OdinRangeQuery(q, 1, max)
          // If there is a zero-width query, it should be the first clause.
          // This is a convention we follow to be able to identify optional clauses easily.
          val clauses = List(zero, oneOrMore)
          new OdinOrQuery(clauses, defaultTokenField)
      }

    case Ast.GreedyRepetitionPattern(pattern, 1, Some(1)) =>
      mkOdinsonQuery(pattern)

    case Ast.GreedyRepetitionPattern(Ast.ConstraintPattern(Ast.Wildcard), n, Some(m)) if n == m =>
      val q = new AllNGramsQuery(defaultTokenField, sentenceLengthField, n)
      Some(q)

    case Ast.GreedyRepetitionPattern(pattern, min, None) =>
      mkOdinsonQuery(pattern).map {
        case q: AllNGramsQuery if q.n == 0 => q
        case q => new OdinRangeQuery(q, min, Int.MaxValue)
      }

    case Ast.GreedyRepetitionPattern(pattern, min, Some(max)) =>
      mkOdinsonQuery(pattern).map {
        case q: AllNGramsQuery if q.n == 0 => q
        case q => new OdinRangeQuery(q, min, max)
      }

  }

  def addConstraint(query: OdinsonQuery, constraint: Option[OdinsonQuery]): OdinsonQuery = {
    (query, constraint) match {
      case (q, None) => q
      case (q: AllNGramsQuery, Some(c)) if q.n == 1 => c
      case (q, Some(c)) => new OdinsonSpanContainingQuery(q, c)
    }
  }

  def mkConstraintQuery(ast: Ast.Constraint): OdinsonQuery = ast match {

    case Ast.FieldConstraint(name, Ast.StringMatcher(string)) =>
      val spanTermQuery = new SpanTermQuery(new Term(name, string))
      new OdinQueryWrapper(maybeMask(spanTermQuery))

    case Ast.FieldConstraint(name, Ast.RegexMatcher(regex)) =>
      val regexpQuery = new RegexpQuery(new Term(name, regex))
      val spanQuery = new SpanMultiTermQueryWrapper(regexpQuery)
      new OdinQueryWrapper(maybeMask(spanQuery))

    case Ast.FuzzyConstraint(name, Ast.StringMatcher(string)) =>
      val fuzzyQuery = new FuzzyQuery(new Term(name, string))
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
      new RegexLabelMatcher(s.r, dependenciesVocabulary)
    case Ast.StringMatcher(s) =>
      dependenciesVocabulary.getId(s) match {
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
      config[Boolean]("compiler.normalizeQueriesToDefaultField")
    )
  }

}
