package ai.lum.odinson.state.fastsql

import java.sql.PreparedStatement

class BatchDbSetter(preparedStatement: PreparedStatement, batch: Boolean = false) {
  val parameterCount = preparedStatement.getParameterMetaData.getParameterCount()
  protected var parameterIndex = 0
  protected var batchCount = 0

  protected def incParameterIndex(): Unit = {
    parameterIndex = parameterIndex % parameterCount + 1
  }

  protected def batch(): Unit = {
    if (batch && parameterCount == parameterIndex) {
      preparedStatement.addBatch()
      batchCount += 1
    }
  }

  def setNext(value: Int): BatchDbSetter = {
    incParameterIndex()
    preparedStatement.setInt(parameterIndex, value)
    batch()
    this
  }

  def setNext(value: String): BatchDbSetter = {
    incParameterIndex()
    preparedStatement.setString(parameterIndex, value)
    batch()
    this
  }

  def reset(): Unit = {
    parameterIndex = 0
    if (batchCount > 0)
      preparedStatement.clearBatch()
    batchCount = 0
  }

  def getBatchCount: Int = batchCount

  def get: PreparedStatement = preparedStatement
}

object BatchDbSetter {

  def apply(preparedStatement: PreparedStatement, batch: Boolean = false): BatchDbSetter = new BatchDbSetter(preparedStatement, batch)
}
