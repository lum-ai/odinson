package ai.lum.odinson.state.sql

import java.sql.Connection
import java.sql.SQLException

import ai.lum.odinson.state.StateException

class TransactionException(message: String, cause: Throwable)
    extends StateException(message, cause)

class RollbackException(message: String, cause: Throwable, val transactionException: TransactionException)
    extends StateException(message, cause)

class Transactor(connection: Connection) {

  def commit(): Unit = connection.commit()

  def rollback(transactionExceptionCause: Exception): Unit = {
    val transactionException = new TransactionException("Transaction failed", transactionExceptionCause)
    try {
      connection.rollback()
    }
    catch {
      case rollbackExceptionCause: SQLException =>
        throw new RollbackException("Rollback failed", rollbackExceptionCause, transactionException)
    }
    throw transactionException
  }

  def transact(f: => Unit): Unit = {
    try {
      f
      commit()
    }
    catch {
      case exception: Exception => rollback(exception)
    }
  }
}
