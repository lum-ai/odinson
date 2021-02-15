package ai.lum.odinson.state

import java.sql.PreparedStatement

class DbSetter(preparedStatement: PreparedStatement) {
  val parameterCount = preparedStatement.getParameterMetaData.getParameterCount()
  protected var parameterIndex = parameterCount

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
