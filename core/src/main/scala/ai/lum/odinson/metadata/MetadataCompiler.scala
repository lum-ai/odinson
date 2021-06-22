package ai.lum.odinson.metadata

import java.time.ZoneId
import java.util.GregorianCalendar

import ai.lum.common.RegexUtils._
import ai.lum.odinson.OdinsonIndexWriter
import ai.lum.odinson.metadata.Ast.StringValue
import org.apache.lucene.index.Term
import org.apache.lucene.search.{BooleanClause, BooleanQuery, Query, RegexpQuery, TermQuery}
import org.apache.lucene.document.DoublePoint
import org.apache.lucene.search.join.{QueryBitSetProducer, ScoreMode, ToParentBlockJoinQuery}
import org.apache.lucene.search.spans.{SpanMultiTermQueryWrapper, SpanNearQuery, SpanQuery, SpanTermQuery}

object MetadataCompiler {

  def mkQuery(pattern: String): Query = {
    val expression = MetadataQueryParser.parseQuery(pattern).get.value
    compile(expression, isNested = false)
  }

  def compile(expr: Ast.BoolExpression, isNested: Boolean): Query = {
    expr match {
      case Ast.OrExpression(clauses) =>
        val builder = new BooleanQuery.Builder
        for (c <- clauses) {
          builder.add(new BooleanClause(compile(c, isNested), BooleanClause.Occur.SHOULD))
        }
        builder.build()

      case Ast.AndExpression(clauses) =>
        val builder = new BooleanQuery.Builder
        for (c <- clauses) {
          builder.add(new BooleanClause(compile(c, isNested), BooleanClause.Occur.MUST))
        }
        builder.build()

      case Ast.NotExpression(expr) =>
        val builder = new BooleanQuery.Builder
        // add the constraint for the type of metadata document
        val fieldType =
          if (isNested) OdinsonIndexWriter.NESTED_TYPE else OdinsonIndexWriter.PARENT_TYPE
        builder.add(new BooleanClause(
          new TermQuery(new Term(OdinsonIndexWriter.TYPE, fieldType)),
          BooleanClause.Occur.MUST
        ))
        builder.add(new BooleanClause(compile(expr, isNested), BooleanClause.Occur.MUST_NOT))
        builder.build()

      case Ast.LessThan(lhs, rhs) =>
        val (field, value, flipped) = handleArgs(lhs, rhs)
        value match {
          case value: Ast.NumberValue if flipped =>
            DoublePoint.newRangeQuery(field.name, value.n + 1, Double.MaxValue)
          case value: Ast.NumberValue =>
            DoublePoint.newRangeQuery(field.name, Double.MinValue, value.n - 1)
        }

      case Ast.LessThanOrEqual(lhs, rhs) =>
        val (field, value, flipped) = handleArgs(lhs, rhs)
        value match {
          case value: Ast.NumberValue if flipped =>
            DoublePoint.newRangeQuery(field.name, value.n, Double.MaxValue)
          case value: Ast.NumberValue =>
            DoublePoint.newRangeQuery(field.name, Double.MinValue, value.n)
        }

      case Ast.GreaterThan(lhs, rhs) =>
        val (field, value, flipped) = handleArgs(lhs, rhs)
        value match {
          case value: Ast.NumberValue if flipped =>
            DoublePoint.newRangeQuery(field.name, Double.MinValue, value.n - 1)
          case value: Ast.NumberValue =>
            DoublePoint.newRangeQuery(field.name, value.n + 1, Double.MaxValue)
        }

      case Ast.GreaterThanOrEqual(lhs, rhs) =>
        val (field, value, flipped) = handleArgs(lhs, rhs)
        value match {
          case value: Ast.NumberValue if flipped =>
            DoublePoint.newRangeQuery(field.name, Double.MinValue, value.n)
          case value: Ast.NumberValue =>
            DoublePoint.newRangeQuery(field.name, value.n, Double.MaxValue)
        }

      case Ast.Equal(lhs, rhs) =>
        val (field, value, flipped) = handleArgs(lhs, rhs)
        value match {
          case value: Ast.NumberValue =>
            DoublePoint.newExactQuery(field.name, value.n)
          case value: Ast.StringValue =>
            // since this is `equal`, we are interested in matching the
            // exact span, not a sub-span
            val tokens = mkTokens(value, exactSpan = true)
            mkQueryFromTokens(field.name, tokens)
        }

      case Ast.NestedExpression(name, expr) =>
        // build child query as specified by user
        val builder = new BooleanQuery.Builder
        builder.add(new BooleanClause(compile(expr, isNested = true), BooleanClause.Occur.MUST))
        // A field by this name
        builder.add(new BooleanClause(
          new TermQuery(new Term(OdinsonIndexWriter.NAME, name)),
          BooleanClause.Occur.MUST
        ))
        val childQuery = builder.build()
        // parentTermQuery gets the parent document for the nested document
        // a field of type: metadata
        val parentTermQuery =
          new TermQuery(new Term(OdinsonIndexWriter.TYPE, OdinsonIndexWriter.PARENT_TYPE))
        val parentFilter = new QueryBitSetProducer(parentTermQuery)
        new ToParentBlockJoinQuery(childQuery, parentFilter, ScoreMode.None)

      case Ast.Contains(field, value) =>
        val tokens = mkTokens(value)
        mkQueryFromTokens(field.name, tokens)
    }
  }

  /** Examines the content of the string and determine if it is a regex.  If so, creates a RegexpQuery,
    * else it makes a SpanQuery, which can later be combined with other SpanQueries,
    * e.g., in a SpanNearQuery. */
  def mkTermQuery(field: String, s: String): SpanQuery = {
    // make the regex to match lucene regular expressions
    val regexPattern = mkCharDelimited("/")
    val regexMatch = regexPattern.r.findFirstIn(s)
    if (regexMatch.isDefined) {
      // remove the leading and training /
      val termPattern = regexMatch.get.drop(1).dropRight(1)
      new SpanMultiTermQueryWrapper(new RegexpQuery(new Term(field, termPattern)))
    } else {
      new SpanTermQuery(new Term(field, s))
    }
  }

  /** Split the normalized form of the StringValue's string, then if you're trying to match
    * an exact span (i.e., the whole string), wrap with the special start and end tokens */
  def mkTokens(value: StringValue, exactSpan: Boolean = false): Seq[String] = {
    // we don't support spaces within a token
    val tokens = value.norm.split("\\s+")
    // to enforce the exact match of the whole field, add special tokens for the start and end
    if (exactSpan) OdinsonIndexWriter.START_TOKEN +: tokens :+ OdinsonIndexWriter.END_TOKEN
    else tokens
  }

  /** SpanNearQuery needs to >= 2 subQueries, so if there is a single query, handle it directly.
    * Else, construct the SpanNearQuery */
  def mkQueryFromTokens(field: String, tokens: Seq[String]): Query = {
    tokens match {
      case Seq(singleToken) => mkTermQuery(field, singleToken)
      case multipleTokens => mkSpanNearQuery(field, multipleTokens)
    }
  }

  /** With slop == 0, we are using the SpanNearQuery to find adjacent/contiguous tokens.
    * This is used instead of a PhraseQuery to support RegeexpQuery as the term queries. */
  def mkSpanNearQuery(field: String, tokens: Seq[String]): Query = {
    // Since we want to match contiguously, we want the tokens to appear
    // in the specified order.
    val builder = new SpanNearQuery.Builder(field, true)
    // allow 0 intervening terms -- they *must* be adjacent
    builder.setSlop(0)
    tokens foreach { token =>
      // add each token in order
      builder.addClause(mkTermQuery(field, token))
    }
    builder.build()
  }

  def handleArgs(lhs: Ast.Value, rhs: Ast.Value): (Ast.FieldValue, Ast.Value, Boolean) = {
    val l = evalValue(lhs)
    val r = evalValue(rhs)
    (l, r) match {
      case (l: Ast.FieldValue, r: Ast.FieldValue) => ???
      case (l: Ast.FieldValue, r: Ast.Value)      => (l, r, false)
      case (l: Ast.Value, r: Ast.FieldValue)      => (r, l, true)
      case (l: Ast.Value, r: Ast.Value)           => ???
    }
  }

  def evalValue(v: Ast.Value): Ast.Value = {
    v match {
      case f: Ast.FunCall => evalFunCall(f)
      case v              => v
    }
  }

  def evalFunCall(f: Ast.FunCall): Ast.Value = {
    f.name match {
      case "date" => evalDate(f.args)
    }
  }

  val months = Map(
    "jan" -> 0,
    "feb" -> 1,
    "mar" -> 2,
    "apr" -> 3,
    "may" -> 4,
    "jun" -> 5,
    "jul" -> 6,
    "aug" -> 7,
    "sep" -> 8,
    "oct" -> 9,
    "nov" -> 10,
    "dec" -> 11,
    "january" -> 0,
    "february" -> 1,
    "march" -> 2,
    "april" -> 3,
    "june" -> 5,
    "july" -> 6,
    "august" -> 7,
    "september" -> 8,
    "october" -> 9,
    "november" -> 10,
    "december" -> 11
  )

  def evalDate(args: Seq[Ast.Value]): Ast.Value = {
    val n = args match {
      case Seq(year: Ast.NumberValue) =>
        val date = new GregorianCalendar(year.n.toInt, 0, 1).getTime()
        val localDate = date.toInstant.atZone(ZoneId.of("UTC")).toLocalDate
        localDate.toEpochDay

      case Seq(year: Ast.NumberValue, month: Ast.NumberValue) =>
        val date = new GregorianCalendar(year.n.toInt, month.n.toInt - 1, 1).getTime()
        val localDate = date.toInstant.atZone(ZoneId.of("UTC")).toLocalDate
        localDate.toEpochDay

      case Seq(year: Ast.NumberValue, month: Ast.NumberValue, day: Ast.NumberValue) =>
        val date = new GregorianCalendar(year.n.toInt, month.n.toInt - 1, day.n.toInt).getTime()
        val localDate = date.toInstant.atZone(ZoneId.of("UTC")).toLocalDate
        localDate.toEpochDay

      case Seq(year: Ast.NumberValue, month: Ast.StringValue) =>
        val date = new GregorianCalendar(year.n.toInt, months(month.norm), 1).getTime()
        val localDate = date.toInstant.atZone(ZoneId.of("UTC")).toLocalDate
        localDate.toEpochDay

      case Seq(year: Ast.NumberValue, month: Ast.StringValue, day: Ast.NumberValue) =>
        val date = new GregorianCalendar(year.n.toInt, months(month.norm), day.n.toInt).getTime()
        val localDate = date.toInstant.atZone(ZoneId.of("UTC")).toLocalDate
        localDate.toEpochDay
    }
    Ast.NumberValue(n)
  }

}
