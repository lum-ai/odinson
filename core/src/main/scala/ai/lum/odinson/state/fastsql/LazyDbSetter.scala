package ai.lum.odinson.state.fastsql

import java.sql.PreparedStatement

class LazyDbSetter(lazyPreparedStatement: LazyPreparedStatement) {
  lazy val preparedStatement = lazyPreparedStatement.get
  lazy val parameterCount = preparedStatement.getParameterMetaData.getParameterCount()
  protected var parameterIndex = 0

  protected def incParameterIndex(): Unit =
    parameterIndex = parameterIndex % parameterCount + 1

  def setNext(value: Int): LazyDbSetter = {
    incParameterIndex()
    preparedStatement.setInt(parameterIndex, value)
    this
  }

  def setNext(value: String): LazyDbSetter = {
    incParameterIndex()
    preparedStatement.setString(parameterIndex, value)
    this
  }

  def get: PreparedStatement = preparedStatement
}

object LazyDbSetter {

  def apply(preparedStatement: LazyPreparedStatement): LazyDbSetter = new LazyDbSetter(preparedStatement)
}