package ai.lum.odinson.state.sql

import java.sql.PreparedStatement

class DbSetter(preparedStatement: PreparedStatement) {
  val parameterCount: Int = preparedStatement.getParameterMetaData.getParameterCount
  protected var parameterIndex = 0

  protected def incParameterIndex(): Unit =
    parameterIndex = parameterIndex % parameterCount + 1

  def setNext(value: Int): DbSetter = {
    incParameterIndex()
    preparedStatement.setInt(parameterIndex, value)
    this
  }

  def setNext(value: String): DbSetter = {
    incParameterIndex()
    preparedStatement.setString(parameterIndex, value)
    this
  }

  def get: PreparedStatement = preparedStatement
}

object DbSetter {

  def apply(preparedStatement: PreparedStatement): DbSetter = new DbSetter(preparedStatement)
}
