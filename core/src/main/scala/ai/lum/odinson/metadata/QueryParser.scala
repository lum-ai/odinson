package ai.lum.odinson.metadata

import fastparse._
import ScriptWhitespace._
import ai.lum.odinson.compiler.Literals

object MetadataQueryParser {

  def parseQuery(query: String): Parsed[Ast.BoolExpression] = {
    parse(query, top(_))
  }

  def top[_: P]: P[Ast.BoolExpression] = {
    or_expression
  }

  def or_expression[_: P]: P[Ast.BoolExpression] = {
    P(and_expression.rep(min = 1, sep = "||")).map {
      case Seq(expr)   => expr
      case expressions => Ast.OrExpression(expressions)
    }
  }

  def and_expression[_: P]: P[Ast.BoolExpression] = {
    P(atomic_expression.rep(min = 1, sep = "&&")).map {
      case Seq(expr)   => expr
      case expressions => Ast.AndExpression(expressions)
    }
  }

  def atomic_expression[_: P]: P[Ast.BoolExpression] = {
    P(cmp_expression | group_expression | nested_expression | contains_expression)
  }

  def contains_expression[_: P]: P[Ast.BoolExpression] = {
    P(field_value ~ "not".!.? ~ "contains"./ ~ string_value).map {
      case (field, None, value)          => Ast.Contains(field, value)
      case (field, Some(negated), value) => Ast.NotExpression(Ast.Contains(field, value))
    }
  }

  def nested_expression[_: P]: P[Ast.BoolExpression] = {
    P(Literals.identifier ~ "{"./ ~ or_expression ~ "}").map {
      case (name, expr) => Ast.NestedExpression(name, expr)
    }
  }

  // only a group_expression can be negated,
  // because we always want parenthesis when applying negation
  def group_expression[_: P]: P[Ast.BoolExpression] = {
    P("!".!.? ~ "(" ~ or_expression ~ ")").map {
      case (None, expr)    => expr
      case (Some(_), expr) => Ast.NotExpression(expr)
    }
  }

  // comparison expression
  def cmp_expression[_: P]: P[Ast.BoolExpression] = {
    P(value ~ cmp_op./ ~ value ~ (cmp_op./ ~ value).rep).map {
      case (lhs, op, rhs, Seq()) =>
        mk_compare(lhs, op, rhs)
      case (lhs, op, rhs, rest) =>
        var prev = rhs
        val first = mk_compare(lhs, op, rhs)
        val clauses = rest.foldLeft(Seq(first)) {
          case (left, (op, expr)) =>
            val r = mk_compare(prev, op, expr)
            prev = expr
            left :+ r
        }
        clauses match {
          case Seq(expr) => expr
          case clauses   => Ast.AndExpression(clauses)
        }
    }
  }

  def cmp_op[_: P]: P[String] = StringIn(">", "<", ">=", "<=", "==", "!=").!

  def mk_compare(lhs: Ast.Value, op: String, rhs: Ast.Value): Ast.BoolExpression = {
    op match {
      case ">"  => Ast.GreaterThan(lhs, rhs)
      case "<"  => Ast.LessThan(lhs, rhs)
      case ">=" => Ast.GreaterThanOrEqual(lhs, rhs)
      case "<=" => Ast.LessThanOrEqual(lhs, rhs)
      case "==" => Ast.Equal(lhs, rhs)
      case "!=" => Ast.NotExpression(Ast.Equal(lhs, rhs))
    }
  }

  def value[_: P]: P[Ast.Value] = {
    P(fun_call | string_value | number_value | field_value)
  }

  def fun_call[_: P]: P[Ast.Value] = {
    P(Literals.identifier ~ "("./ ~ value.rep(sep = ",") ~ ")").map {
      case (name, args) => Ast.FunCall(name, args)
    }
  }

  def string_value[_: P]: P[Ast.StringValue] = {
    Literals.quotedString.map(Ast.StringValue)
  }

  def number_value[_: P]: P[Ast.NumberValue] = {
    Literals.unsignedInt.map(n => Ast.NumberValue(n))
  }

  def field_value[_: P]: P[Ast.FieldValue] = {
    field_attribute_value | field_base_value
  }

  def field_base_value[_: P]: P[Ast.FieldValue] = {
    Literals.identifier.map(Ast.FieldValue)
  }

  def field_attribute_value[_: P]: P[Ast.FieldValue] = {
    P(Literals.identifier ~~ "."./ ~~ Literals.identifier).map {
      case (field, attribute) => Ast.FieldValue(s"$field.$attribute")
    }
  }

}
