package ai.lum.odinson.metadata

import java.time.ZoneId
import java.time.LocalDate
import java.text.DateFormat
import java.util.GregorianCalendar
import org.apache.lucene.search.Query
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.MatchAllDocsQuery

object Compiler {

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
                val builder = new BooleanQuery.Builder
                builder.add(new BooleanClause(new MatchAllDocsQuery, BooleanClause.Occur.MUST))
                builder.add(new BooleanClause(compile(expr), BooleanClause.Occur.MUST_NOT))
                builder.build()

            case Ast.LessThan(lhs, rhs) =>
                val l = evalValue(lhs)
                val r = evalValue(rhs)
                ???

            case Ast.LessThanOrEqual(lhs, rhs) =>
                val l = evalValue(lhs)
                val r = evalValue(rhs)
                ???

            case Ast.GreaterThan(lhs, rhs) =>
                val l = evalValue(lhs)
                val r = evalValue(rhs)
                ???

            case Ast.GreaterThanOrEqual(lhs, rhs) =>
                val l = evalValue(lhs)
                val r = evalValue(rhs)
                ???

            case Ast.Equal(lhs, rhs) =>
                val l = evalValue(lhs)
                val r = evalValue(rhs)
                ???

            case Ast.NotEqual(lhs, rhs) =>
                val l = evalValue(lhs)
                val r = evalValue(rhs)
                ???
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

}