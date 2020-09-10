package ai.lum.odinson.state.fastsql

import java.sql.Connection

import ai.lum.common.TryWithResources.using
import ai.lum.odinson.state.ResultItem
import ai.lum.odinson.state.State
import ai.lum.odinson.state.sql.DbGetter
import ai.lum.odinson.state.sql.IdProvider
import ai.lum.odinson.state.sql.ReadNode
import ai.lum.odinson.state.sql.SqlResultItem
import ai.lum.odinson.state.sql.Transactor

import scala.collection.mutable.ArrayBuffer

class FastSqlState(val connection: Connection, protected val factoryIndex: Long, protected val stateIndex: Long) extends State {
  protected val mentionsTable = s"mentions_${factoryIndex}_$stateIndex"
  protected val idProvider = new IdProvider()
  protected val transactor = new Transactor(connection)
  protected var closed = false

  // TODO: If nothing ever gets written to the tables, then they don't need to exist
  // and they certainly don't need to be indexed.  Keep track of how many times this happens.
  create()

  def create(): Unit = {
    transactor.transact {
      createTable()
      createIndexes()
    }
  }

  def createTable(): Unit = {
    // This query is used only once, so it won't do any good to cache it.
    val sql = s"""
      CREATE TABLE IF NOT EXISTS $mentionsTable (
        doc_base INT NOT NULL,            -- offset corresponding to lucene segment
        doc_id INT NOT NULL,              -- relative to lucene segment (not global)
        doc_index INT NOT NULL,           -- document index
        label VARCHAR(50) NOT NULL,       -- mention label if parent or label of NamedCapture if child
        name VARCHAR(50) NOT NULL,        -- name of extractor if parent or name of NamedCapture if child
        id INT NOT NULL,                  -- id for row, issued by State
        parent_id INT NOT NULL,           -- id of parent, -1 if root node
        child_count INT NOT NULL,         -- number of children
        child_label VARCHAR(50) NOT NULL, -- label of child, because label is for parent
        start_token INT NOT NULL,         -- index of mention first token (inclusive)
        end_token INT NOT NULL,           -- index of mention last token (exclusive)
      );
    """
    using(connection.createStatement()) { statement =>
      statement.executeUpdate(sql)
    }
  }

  def createIndexes(): Unit = {
    {
      // This query is used only once, so it won't do any good to cache it.
      val sql =
        s"""
          CREATE INDEX IF NOT EXISTS ${mentionsTable}_index_main
          ON $mentionsTable(doc_base, doc_id, label);
        """
      using(connection.createStatement()) { statement =>
        statement.executeUpdate(sql)
      }
    }

    {
      // This query is used only once, so it won't do any good to cache it.
      val sql =
        s"""
          CREATE INDEX IF NOT EXISTS ${mentionsTable}_index_doc_id
          ON $mentionsTable(doc_base, label);
        """
      using(connection.createStatement()) { statement =>
        statement.executeUpdate(sql)
      }
    }
  }

  val addResultItemsStatement: LazyPreparedStatement = LazyPreparedStatement(connection,
    s"""
      INSERT INTO $mentionsTable
        (doc_base, doc_id, doc_index, label, name, id, parent_id, child_count, child_label, start_token, end_token)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ;
    """
  )

  protected def executeBatch(dbSetter: BatchDbSetter): Unit = {
    dbSetter.get.executeBatch()
    dbSetter.reset()
  }

  override def addResultItems(resultItems: Iterator[ResultItem]): Unit = {
    if (resultItems.nonEmpty) {
      val dbSetter = BatchDbSetter(addResultItemsStatement.get, batch = true)

      transactor.transact {
        resultItems.foreach { resultItem =>
          val stateNodes = SqlResultItem.toWriteNodes(resultItem, idProvider)

          stateNodes.foreach { stateNode =>
            val batchCount = dbSetter
                .setNext(resultItem.segmentDocBase)
                .setNext(resultItem.segmentDocId)
                .setNext(resultItem.docIndex)
                .setNext(resultItem.label)
                .setNext(stateNode.name)
                .setNext(stateNode.id)
                .setNext(stateNode.parentId)
                .setNext(stateNode.childNodes.length)
                .setNext(stateNode.label)
                .setNext(stateNode.start)
                .setNext(stateNode.end)
                .getBatchCount
            if (batchCount >= FastSqlState.batchCountLimit)
              executeBatch(dbSetter)
          }
        }
        if (dbSetter.getBatchCount > 0)
          executeBatch(dbSetter)
      }
    }
  }

  val getDocIdsStatement: LazyPreparedStatement = LazyPreparedStatement(connection,
    s"""
      SELECT DISTINCT doc_id
      FROM $mentionsTable
      WHERE doc_base=? AND label=?
      ORDER BY doc_id
      ;
    """
  )

  // TODO: This should be in a separate, smaller table so that
  // looking through it is faster and no DISTINCT is necessary.
  // See MemoryState for guidance.

  /** Returns the segment-specific doc-ids that correspond
   *  to lucene documents that contain a mention with the
   *  specified label
   */
  override def getDocIds(docBase: Int, label: String): Array[Int] = {
    val resultSet = BatchDbSetter(getDocIdsStatement.get)
        .setNext(docBase)
        .setNext(label)
        .get
        .executeQuery()

    DbGetter(resultSet).map { dbGetter =>
      dbGetter.getInt
    }.toArray
  }

  val getResultItemsStatement: LazyPreparedStatement = LazyPreparedStatement(connection,
    s"""
      SELECT doc_index, name, id, parent_id, child_count, child_label, start_token, end_token
      FROM $mentionsTable
      WHERE doc_base=? AND doc_id=? AND label=?
      ORDER BY id
      ;
    """
  )

  override def getResultItems(docBase: Int, docId: Int, label: String): Array[ResultItem] = {
    val resultSet = new BatchDbSetter(getResultItemsStatement.get)
        .setNext(docBase)
        .setNext(docId)
        .setNext(label)
        .get
        .executeQuery()
    val readNodes = ArrayBuffer.empty[ReadNode]
    val resultItems = ArrayBuffer.empty[ResultItem]

    DbGetter(resultSet).foreach { dbGetter =>
      val docIndex = dbGetter.getInt
      val name = dbGetter.getStr
      val id = dbGetter.getInt
      val parentId = dbGetter.getInt
      val childCount = dbGetter.getInt
      val childLabel = dbGetter.getStr
      val start = dbGetter.getInt
      val end = dbGetter.getInt

      readNodes += ReadNode(docIndex, name, id, parentId, childCount, childLabel, start, end)
      if (parentId == -1) {
        resultItems += SqlResultItem.fromReadNodes(docBase, docId, label, readNodes)
        readNodes.clear()
      }
    }
    resultItems.toArray
  }

  override def close(): Unit = {
    if (!closed) {
      addResultItemsStatement.close()
      getDocIdsStatement.close()
      getResultItemsStatement.close()
      closed = true
      drop()
    }
  }

  // See https://examples.javacodegeeks.com/core-java/sql/delete-all-table-rows-example/.
  // "TRUNCATE is faster than DELETE since it does not generate rollback information and does not
  // fire any delete triggers."  There's also no need to update indexes.
  // However, DROP is what we want.  The tables and indexes should completely disappear.
  protected def drop(): Unit = {
    // This query is used only once, so it won't do any good to cache it.
    val sql = s"""
      DROP TABLE IF EXISTS $mentionsTable
      ;
    """
    using(connection.createStatement()) { statement =>
      transactor.transact {
        statement.executeUpdate(sql)
      }
    }
  }
}

object FastSqlState {
  val batchCountLimit = 500
}
