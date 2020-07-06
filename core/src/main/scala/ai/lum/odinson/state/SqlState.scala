package ai.lum.odinson.state

import java.sql.Connection
import java.util.concurrent.atomic.AtomicLong

import scala.collection.mutable.ArrayBuffer

import ai.lum.common.ConfigUtils._
import ai.lum.common.TryWithResources.using
import com.typesafe.config.Config
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

class SqlState(val connection: Connection, protected val factoryIndex: Long, protected val stateIndex: Long) extends State {

  init()

  def init(): Unit = {
    createTable()
    createIndexes()
  }

  def createTable(): Unit = {
    val sql = s"""
      CREATE TABLE IF NOT EXISTS mentions_${factoryIndex}_$stateIndex (
        doc_base INT NOT NULL,       -- offset corresponding to lucene segment
        doc_id INT NOT NULL,         -- relative to lucene segment (not global)
        label VARCHAR(50) NOT NULL,  -- mention label
        start_token INT NOT NULL,    -- index of mention first token (inclusive)
        end_token INT NOT NULL,      -- index of mention last token (exclusive)
      );
    """
    connection.createStatement().executeUpdate(sql)
  }

  def createIndexes(): Unit = {
    {
      val sql =
        s"""
          CREATE INDEX IF NOT EXISTS mentions_${factoryIndex}_${stateIndex}_index
          ON mentions_${factoryIndex}_${stateIndex}(doc_base, doc_id, label);
        """
      connection.createStatement().executeUpdate(sql)
    }

    {
      val sql =
        s"""
          CREATE INDEX IF NOT EXISTS docIds_${factoryIndex}_${stateIndex}_index
          ON mentions_${factoryIndex}_${stateIndex}(doc_base, label);
        """
      connection.createStatement().executeUpdate(sql)
    }
  }

  // Reuse the same connection and prepared statement.
  // TODO Group the mentions and insert multiple at a time.
  override def addMentions(mentions: Iterator[(Int, Int, String, Int, Int)]): Unit = {
    val sql = s"""
      INSERT INTO mentions_${factoryIndex}_$stateIndex
        (doc_base, doc_id, label, start_token, end_token)
      VALUES (?, ?, ?, ?, ?)
      ;
    """
    val stmt = connection.prepareStatement(sql)

    // FIXME this should be altered to add several mentions in a single call
    mentions.foreach { mention =>
      stmt.setInt(1, mention._1)
      stmt.setInt(2, mention._2)
      stmt.setString(3, mention._3)
      stmt.setInt(4, mention._4)
      stmt.setInt(5, mention._5)
      stmt.executeUpdate()
    }
  }

  // TODO: This should be in a separate, smaller table so that
  // looking through it is faster and no DISTINCT is necessary.
  // See MemoryState for guidance.

  /** Returns the segment-specific doc-ids that correspond
   *  to lucene documents that contain a mention with the
   *  specified label
   */
  override def getDocIds(docBase: Int, label: String): Array[Int] = {
    val sql = s"""
      SELECT DISTINCT doc_id
      FROM mentions_${factoryIndex}_$stateIndex
      WHERE doc_base=? AND label=?
      ORDER BY doc_id
      ;
    """
    val stmt = connection.prepareStatement(sql)
    stmt.setInt(1, docBase)
    stmt.setString(2, label)
    val results = stmt.executeQuery()
    val docIds = ArrayBuffer.empty[Int]
    while (results.next()) {
      docIds += results.getInt("doc_id")
    }
    docIds.toArray
  }

  override def getMatches(
    docBase: Int,
    docId: Int,
    label: String
  ): Array[(Int, Int)] = {
    val sql = s"""
      SELECT start_token, end_token
      FROM mentions_${factoryIndex}_$stateIndex
      WHERE doc_base=? AND doc_id=? AND label=?
      ORDER BY start_token, end_token
      ;
    """
    val stmt = connection.prepareStatement(sql)
    stmt.setInt(1, docBase)
    stmt.setInt(2, docId)
    stmt.setString(3, label)
    val results = stmt.executeQuery()
    val matches = ArrayBuffer.empty[(Int, Int)]
    while (results.next()) {
      val start = results.getInt("start_token")
      val end = results.getInt("end_token")
      matches += Tuple2(start, end)
    }
    matches.toArray
  }

  def clear(): Unit = {
    delete()
  }

  /** delete all mentions from the state */
  // See https://examples.javacodegeeks.com/core-java/sql/delete-all-table-rows-example/.
  // "TRUNCATE is faster than DELETE since it does not generate rollback information and does not
  // fire any delete triggers."
  protected def delete(): Unit = {
    val sql = s"""DELETE FROM mentions_${factoryIndex}_$stateIndex;""" // TODO test TRUNCATE
    connection.createStatement().executeUpdate(sql)
  }
}

class SqlStateFactory(dataSource: HikariDataSource, index: Long) extends StateFactory {
  protected var count: AtomicLong = new AtomicLong

  override def usingState[T](function: State => T): T = {
    using(dataSource.getConnection) { connection =>
      // Does the state have to close itself?
      val state = new SqlState(connection, index, count.getAndIncrement)
      function(state)
    }
  }
}

object SqlStateFactory {
  protected var count: AtomicLong = new AtomicLong

  def apply(config: Config): SqlStateFactory = {
    val jdbcUrl = config[String]("state.jdbc.url")
    val dataSource: HikariDataSource = {
      val config = new HikariConfig
      config.setJdbcUrl(jdbcUrl)
      config.setPoolName("odinson")
      config.setUsername("")
      config.setPassword("")
      config.setMaximumPoolSize(10) // Don't do this?
      config.setMinimumIdle(2)
      config.addDataSourceProperty("cachePrepStmts", "true")
      config.addDataSourceProperty("prepStmtCacheSize", "256")
      config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
      new HikariDataSource(config)
    }

    new SqlStateFactory(dataSource, count.getAndIncrement)
  }
}
