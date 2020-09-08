package ai.lum.odinson.state

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

class DbSetter(preparedStatement: PreparedStatement) {
  val parameterCount = preparedStatement.getParameterMetaData.getParameterCount()
  protected var parameterIndex = 0
  var batched = false

  protected def incParameterIndex(): Unit = {
    if (parameterCount == parameterIndex) {
      preparedStatement.addBatch()
      batched = true
    }
    parameterIndex = parameterIndex % parameterCount + 1
  }

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

  def reset(): Unit = {
    parameterIndex = 0
    if (batched)
      preparedStatement.clearBatch()
    batched = false
  }

  def get: PreparedStatement = preparedStatement
}

object DbSetter {

  def apply(preparedStatement: PreparedStatement): DbSetter = new DbSetter(preparedStatement)
}
