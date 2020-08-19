package ai.lum.odinson.state

import java.sql.Connection
import java.sql.PreparedStatement

class LazyPreparedStatement(connection: Connection, sql: String) {
  var preparedStatementOpt: Option[PreparedStatement] = None

  def get: PreparedStatement = {
    preparedStatementOpt.getOrElse {
      val preparedStatement = connection.prepareStatement(sql)

      preparedStatementOpt = Some(preparedStatement)
      preparedStatement
    }
  }

  def close(): Unit = {
    preparedStatementOpt.foreach { preparedStatement =>
      preparedStatementOpt = None
      preparedStatement.close
    }
  }
}

object LazyPreparedStatement {

  def apply(connection: Connection, sql: String): LazyPreparedStatement = new LazyPreparedStatement(connection,sql)
}
