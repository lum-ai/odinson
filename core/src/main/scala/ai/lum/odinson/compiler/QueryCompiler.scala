package ai.lum.odinson.compiler

import java.io.File
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import com.typesafe.config._
import ai.lum.common.ConfigUtils._
import ai.lum.odinson.lucene._
import ai.lum.odinson.digraph._

class QueryCompiler(
    val allTokenFields: Seq[String],
    val defaultTokenField: String,
    val sentenceLengthField: String,
    val dependenciesField: String,
    val incomingTokenField: String,
    val outgoingTokenField: String,
    val dependenciesVocabulary: Vocabulary,
    val lowerCaseQueriesToDefaultField: Boolean
) {

  val parser = new QueryParser(allTokenFields, defaultTokenField, lowerCaseQueriesToDefaultField)

  def compile(pattern: String): OdinQuery = {
    val ast = parser.parse(pattern)
    val query = mkOdinQuery(ast)
    query
  }

  /**
    * Join an Odinson pattern with an optional Lucene query meant to filter the parent
    * @param odinsonPattern An Odison pattern.
    * @param parentQuery An optional Lucene query string meant to filter the documents to which the odinsonPattern is applied.
    */
  def compile(odinsonPattern: String, parentQuery: Option[String]): OdinQuery = {
    val oq = compile(odinsonPattern)
    if (parentQuery.isEmpty) {
      oq
    } else {
      // TODO: turn this into a Lucene query
      // Do we have to provide a default field, or can we assume it specifies the fields it wants to match?
      val pq = parentQuery.get
      // FIXME: join queries!
      oq
    }
  }

  def mkOdinQuery(ast: Ast.Pattern): OdinQuery = ast match {

    case Ast.ConstraintPattern(constraint) =>
      // compile a token constraint
      mkConstraintQuery(constraint)

    case Ast.NamedCapturePattern(name, pattern) =>
      // wrap the pattern in a named capture
      new OdinQueryNamedCapture(mkOdinQuery(pattern), name)

    case Ast.DisjunctivePattern(patterns) =>
      // separate zero-width queries from the rest
      val (zs, qs) = patterns.map(mkOdinQuery).distinct.partition {
        case q: AllNGramsQuery if q.n == 0 => true
        case _ => false
      }
      (zs, qs) match {
        case (Seq(), Seq()) => sys.error("OR without clauses")
        case (Seq(), Seq(q)) => q
        case (Seq(), qs) => new OdinOrQuery(qs, defaultTokenField)
        case (zs, Seq()) => zs.head
        case (zs, qs) =>
          // If there is a zero-width query, it should be the first clause.
          // This is a convention we follow to be able to identify optional clauses easily.
          new OdinOrQuery(zs.head +: qs, defaultTokenField)
      }

    case Ast.ConcatenatedPattern(patterns) =>
      val clauses = patterns.map(mkOdinQuery).filter {
        case q: AllNGramsQuery if q.n == 0 => false
        case q@_ => true
      }
      clauses match {
        case Seq() => new AllNGramsQuery(defaultTokenField, sentenceLengthField, 0)
        case Seq(q) => q
        case qs => new OdinConcatQuery(qs, defaultTokenField, sentenceLengthField)
      }

    case Ast.GreedyRepetitionPattern(pattern@_, 0, Some(0)) =>
      new AllNGramsQuery(defaultTokenField, sentenceLengthField, 0)

    case Ast.GreedyRepetitionPattern(pattern, 0, Some(1)) => // TODO support greedy/lazy option
      mkOdinQuery(pattern) match {
        case q: AllNGramsQuery if q.n == 0 => q
        case one =>
          val zero = new AllNGramsQuery(defaultTokenField, sentenceLengthField, 0)
          // If there is a zero-width query, it should be the first clause.
          // This is a convention we follow to be able to identify optional clauses easily.
          val clauses = List(zero, one)
          new OdinOrQuery(clauses, defaultTokenField)
      }

    case Ast.GreedyRepetitionPattern(pattern, 0, max) =>
      mkOdinQuery(pattern) match {
        case q: AllNGramsQuery if q.n == 0 => q
        case q =>
          val zero = new AllNGramsQuery(defaultTokenField, sentenceLengthField, 0)
          val oneOrMore = new OdinRangeQuery(q, 1, max, quantifierType)
          // If there is a zero-width query, it should be the first clause.
          // This is a convention we follow to be able to identify optional clauses easily.
          val clauses = List(zero, oneOrMore)
          new OdinOrQuery(clauses, defaultTokenField)
      }

    case Ast.GreedyRepetitionPattern(pattern, 1, Some(1)) =>
      mkOdinQuery(pattern)

    case Ast.GreedyRepetitionPattern(Ast.ConstraintPattern(Ast.Wildcard), n, m) if n == m =>
      new AllNGramsQuery(defaultTokenField, sentenceLengthField, n)

    case Ast.GreedyRepetitionPattern(pattern, min, max) =>
      mkOdinQuery(pattern) match {
        case q: AllNGramsQuery if q.n == 0 => q
        case q => new OdinRangeQuery(q, min, max, quantifierType)
      }

    case Ast.AssertionPattern(Ast.SentenceStartAssertion) =>
      new DocStartQuery(defaultTokenField)

    case Ast.AssertionPattern(Ast.SentenceEndAssertion) =>
      new DocEndQuery(defaultTokenField, sentenceLengthField)

    case Ast.GraphTraversalPattern(src, tr, dst) =>
      val traversal = mkGraphTraversal(tr)
      val srcQuery = addConstraint(mkOdinQuery(src), mkStartConstraint(traversal))
      val dstQuery = addConstraint(mkOdinQuery(dst), mkEndConstraint(traversal))
      // if NoTraversal then return srcQuery to avoid deserialization
      traversal match {
        case NoTraversal => srcQuery
        case FailTraversal => new FailQuery(defaultTokenField)
        case _ => new GraphTraversalQuery(defaultTokenField, dependenciesField, srcQuery, traversal, dstQuery)
      }

  }

  def addConstraint(query: OdinQuery, constraint: Option[OdinQuery]): OdinQuery = (query, constraint) match {
    case (q, None) => q
    case (q:AllNGramsQuery, Some(c)) if q.n == 1 => c
    case (q, Some(c)) => new OdinSpanContainingQuery(q, c)
  }

  def mkConstraintQuery(ast: Ast.Constraint): OdinQuery = ast match {

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
  def mkStartConstraint(gt: GraphTraversal): Option[OdinQuery] = gt match {
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
  def mkEndConstraint(gt: GraphTraversal): Option[OdinQuery] = gt match {
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

  def fromConfig(path: String): QueryCompiler = {
    val config = ConfigFactory.load()
    fromConfig(config[Config](path))
  }

  def fromConfig(config: Config): QueryCompiler = {
    new QueryCompiler(
      config[List[String]]("allTokenFields"),
      config[String]("defaultTokenField"),
      config[String]("sentenceLengthField"),
      config[String]("dependenciesField"),
      config[String]("incomingTokenField"),
      config[String]("outgoingTokenField"),
      Vocabulary.fromFile(config[File]("dependenciesVocabulary")),
      config[Boolean]("lowerCaseQueriesToDefaultField")
    )
  }

}
