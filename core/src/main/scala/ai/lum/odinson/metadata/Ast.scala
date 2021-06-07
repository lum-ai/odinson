package ai.lum.odinson.metadata

object Ast {

    sealed trait BoolExpression
    case class OrExpression(clauses: Seq[BoolExpression]) extends BoolExpression
    case class AndExpression(clauses: Seq[BoolExpression]) extends BoolExpression
    case class NotExpression(expr: BoolExpression) extends BoolExpression
    case class LessThan(lhs: Value, rhs: Value) extends BoolExpression
    case class LessThanOrEqual(lhs: Value, rhs: Value) extends BoolExpression
    case class GreaterThan(lhs: Value, rhs: Value) extends BoolExpression
    case class GreaterThanOrEqual(lhs: Value, rhs: Value) extends BoolExpression
    case class Equal(lhs: Value, rhs: Value) extends BoolExpression
    case class NotEqual(lhs: Value, rhs: Value) extends BoolExpression

    sealed trait Value
    case class NumberValue(n: Long) extends Value
    case class StringValue(s: String) extends Value
    case class FunCall(name: String, args: Seq[Value]) extends Value
    case class FieldValue(name: String) extends Value

}
