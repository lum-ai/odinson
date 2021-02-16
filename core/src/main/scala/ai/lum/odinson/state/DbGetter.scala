package ai.lum.odinson.state

import java.sql.ResultSet

class DbGetter(resultSet: ResultSet) extends Iterator[DbGetter] {
  val columnCount = resultSet.getMetaData.getColumnCount
  protected var parameterIndex = columnCount
  protected var checkedNext = false
  protected var checkedNextResult = false

  protected def incParameterIndex(): Unit =
    parameterIndex = parameterIndex % columnCount + 1

  def getInt: Int = {
    incParameterIndex()
    resultSet.getInt(parameterIndex)
  }

  def getStr: String = {
    incParameterIndex()
    resultSet.getString(parameterIndex)
  }

  override def hasNext: Boolean =
    if (checkedNext)
      checkedNextResult
    else {
      checkedNextResult = resultSet.next()
      checkedNext = true
      checkedNextResult
    }


  override def next(): DbGetter = {
    if (!checkedNext)
      resultSet.next()
    checkedNext = false
    this
  }
}

object DbGetter {

  def apply(resultSet: ResultSet): DbGetter = new DbGetter(resultSet)
}
