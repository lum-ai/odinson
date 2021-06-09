package ai.lum.odinson.metadata

import java.time.ZoneId
import java.time.LocalDate
import java.text.DateFormat
import java.util.GregorianCalendar

import ai.lum.odinson.metadata.MetadataCompiler.compile
import org.apache.lucene.index.Term
import org.apache.lucene.search.Query
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.lucene.document.DoublePoint

object MetadataCompiler {

    def mkQuery(pattern: String): Query = {
        val expression = MetadataQueryParser.parseQuery(pattern).get.value
        compile(expression)
    }

    def compile(expr: Ast.BoolExpression): Query = {
        expr match {
            case Ast.OrExpression(clauses) =>
                val builder = new BooleanQuery.Builder
                for (c <- clauses) {
                    builder.add(new BooleanClause(compile(c), BooleanClause.Occur.SHOULD))
                }
                builder.build()

            case Ast.AndExpression(clauses) =>
                val builder = new BooleanQuery.Builder
                for (c <- clauses) {
                    builder.add(new BooleanClause(compile(c), BooleanClause.Occur.MUST))
                }
                builder.build()

            case Ast.NotExpression(expr) =>
                buildNegation(compile(expr))


            case Ast.LessThan(lhs, rhs) =>
                val (field, value, flipped) = handleArgs(lhs, rhs)
                value match {
                    case value: Ast.NumberValue =>
                        if (flipped) {
                            DoublePoint.newRangeQuery(field.name, value.n + 1, Double.MaxValue)
                        } else {
                            DoublePoint.newRangeQuery(field.name, Double.MinValue, value.n - 1)
                        }
                }

            case Ast.LessThanOrEqual(lhs, rhs) =>
                val (field, value, flipped) = handleArgs(lhs, rhs)
                value match {
                    case value: Ast.NumberValue =>
                        if (flipped) {
                            DoublePoint.newRangeQuery(field.name, value.n, Double.MaxValue)
                        } else {
                            DoublePoint.newRangeQuery(field.name, Double.MinValue, value.n)
                        }
                }

            case Ast.GreaterThan(lhs, rhs) =>
                val (field, value, flipped) = handleArgs(lhs, rhs)
                value match {
                    case value: Ast.NumberValue =>
                        if (flipped) {
                            DoublePoint.newRangeQuery(field.name, Double.MinValue, value.n - 1)
                        } else {
                            DoublePoint.newRangeQuery(field.name, value.n + 1, Double.MaxValue)
                        }
                }

            case Ast.GreaterThanOrEqual(lhs, rhs) =>
                val (field, value, flipped) = handleArgs(lhs, rhs)
                value match {
                    case value: Ast.NumberValue =>
                        if (flipped) {
                            DoublePoint.newRangeQuery(field.name, Double.MinValue, value.n)
                        } else {
                            DoublePoint.newRangeQuery(field.name, value.n, Double.MaxValue)
                        }
                }

            case Ast.Equal(lhs, rhs) =>
                val (field, value, flipped) = handleArgs(lhs, rhs)
                value match {
                    case value: Ast.NumberValue =>
                        DoublePoint.newExactQuery(field.name, value.n)
                    case value: Ast.StringValue =>
                        new TermQuery(new Term(field.name, value.s))
                }

            case Ast.NotEqual(lhs, rhs) =>
                val (field, value, flipped) = handleArgs(lhs, rhs)
                val query = value match {
                    case value: Ast.NumberValue =>
                        DoublePoint.newExactQuery(field.name, value.n)
                    case value: Ast.StringValue =>
                        new TermQuery(new Term(field.name, value.s))
                }
                buildNegation(query)
        }
    }

    def handleArgs(lhs: Ast.Value, rhs: Ast.Value): (Ast.FieldValue, Ast.Value, Boolean) = {
        val l = evalValue(lhs)
        val r = evalValue(rhs)
        (l, r) match {
            case (l:Ast.FieldValue, r:Ast.FieldValue) => ???
            case (l:Ast.FieldValue, r:Ast.Value) => (l, r, false)
            case (l:Ast.Value, r:Ast.FieldValue) => (r, l, true)
            case (l:Ast.Value, r:Ast.Value) => ???
        }
    }

    def evalValue(v: Ast.Value): Ast.Value = {
        v match {
            case f: Ast.FunCall => evalFunCall(f)
            case v => v
        }
    }

    def evalFunCall(f: Ast.FunCall): Ast.Value = {
        f.name match {
            case "date" => evalDate(f.args)
        }
    }

    val months = Map(
        "jan" -> 0, "feb" -> 1, "mar" -> 2, "apr" -> 3, "may" -> 4,
        "jun" -> 5, "jul" -> 6, "aug" -> 7, "sep" -> 8, "oct" -> 9,
        "nov" -> 10, "dec" -> 11, "january" -> 0, "february" -> 1,
        "march" -> 2, "april" -> 3, "june" -> 5, "july" -> 6, "august" -> 7,
        "september" -> 8, "october" -> 9, "november" -> 10, "december" -> 11,
    )

    def evalDate(args: Seq[Ast.Value]): Ast.Value = {
        val n = args match {
            case Seq(year: Ast.NumberValue) =>
                val date = new GregorianCalendar(year.n.toInt, 0, 1).getTime()
                val localDate = date.toInstant.atZone(ZoneId.of("UTC")).toLocalDate
                localDate.toEpochDay

            case Seq(year: Ast.NumberValue, month: Ast.NumberValue) =>
                val date = new GregorianCalendar(year.n.toInt, month.n.toInt-1, 1).getTime()
                val localDate = date.toInstant.atZone(ZoneId.of("UTC")).toLocalDate
                localDate.toEpochDay

            case Seq(year: Ast.NumberValue, month: Ast.NumberValue, day: Ast.NumberValue) =>
                val date = new GregorianCalendar(year.n.toInt, month.n.toInt-1, day.n.toInt).getTime()
                val localDate = date.toInstant.atZone(ZoneId.of("UTC")).toLocalDate
                localDate.toEpochDay

            case Seq(year: Ast.NumberValue, month: Ast.StringValue) =>
                val date = new GregorianCalendar(year.n.toInt, months(month.s.toLowerCase), 1).getTime()
                val localDate = date.toInstant.atZone(ZoneId.of("UTC")).toLocalDate
                localDate.toEpochDay

            case Seq(year: Ast.NumberValue, month: Ast.StringValue, day: Ast.NumberValue) =>
                val date = new GregorianCalendar(year.n.toInt, months(month.s.toLowerCase), day.n.toInt).getTime()
                val localDate = date.toInstant.atZone(ZoneId.of("UTC")).toLocalDate
                localDate.toEpochDay
        }
        Ast.NumberValue(n)
    }

  def buildNegation(query: Query): Query = {
      val builder = new BooleanQuery.Builder
      builder.add(new BooleanClause(new MatchAllDocsQuery, BooleanClause.Occur.SHOULD))
      builder.add(new BooleanClause(new TermQuery(new Term("type", "metadata")), BooleanClause.Occur.MUST))
      builder.add(new BooleanClause(query, BooleanClause.Occur.MUST_NOT))
      builder.build()
  }

}
