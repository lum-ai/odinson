package ai.lum.odinson.state

import scala.collection.mutable.ArrayBuffer
import com.zaxxer.hikari.{ HikariConfig, HikariDataSource }
import ai.lum.common.TryWithResources.using

class SqlState(val url: String, protected val index: Long) extends State {
  private val ds = {
    val config = new HikariConfig
    config.setJdbcUrl(url)
    // TODO get from config?
    config.setPoolName("odinson")
    config.setUsername("")
    config.setPassword("")
    config.setMaximumPoolSize(10)
    config.setMinimumIdle(2)
    config.addDataSourceProperty("cachePrepStmts", "true")
    config.addDataSourceProperty("prepStmtCacheSize", "256")
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
    new HikariDataSource(config)
  }

  init()

  def close(): Unit = {
    ds.close()
  }

  def init(): Unit = {
    createTable()
    createIndexes()
  }

  def createTable(): Unit = {
    using(ds.getConnection()) { conn =>
      val sql = s"""
        CREATE TABLE IF NOT EXISTS mentions_$index (
          doc_base INT NOT NULL,       -- offset corresponding to lucene segment
          doc_id INT NOT NULL,         -- relative to lucene segment (not global)
          label VARCHAR(50) NOT NULL,  -- mention label
          start_token INT NOT NULL,    -- index of mention first token (inclusive)
          end_token INT NOT NULL,      -- index of mention last token (exclusive)
        );
      """
      conn.createStatement().executeUpdate(sql)
    }
  }

  def createIndexes(): Unit = {
    using(ds.getConnection()) { conn =>
      {
        val sql =
          s"""
            CREATE INDEX IF NOT EXISTS mentions_${index}_index
            ON mentions_${index}(doc_base, doc_id, label);
          """
        conn.createStatement().executeUpdate(sql)
      }

      {
        val sql =
          s"""
            CREATE INDEX IF NOT EXISTS docIds_${index}_index
            ON mentions_${index}(doc_base, label);
          """
        conn.createStatement().executeUpdate(sql)
      }
    }
  }

  def addMention(
    docBase: Int,
    docId: Int,
    label: String,
    startToken: Int,
    endToken: Int
  ): Unit = {
    using(ds.getConnection()) { conn =>
      val sql = s"""
        INSERT INTO mentions_$index
          (doc_base, doc_id, label, start_token, end_token)
        VALUES (?, ?, ?, ?, ?)
        ;
      """
      val stmt = conn.prepareStatement(sql)
      stmt.setInt(1, docBase)
      stmt.setInt(2, docId)
      stmt.setString(3, label)
      stmt.setInt(4, startToken)
      stmt.setInt(5, endToken)
      stmt.executeUpdate()
    }
  }

  // Reuse the same connection and prepared statement.
  // TODO Group the mentions and insert multiple at a time.
  override def addMentions(mentions: Iterator[(Int, Int, String, Int, Int)]): Unit = {
    using(ds.getConnection()) { conn =>
      val sql = s"""
        INSERT INTO mentions_$index
          (doc_base, doc_id, label, start_token, end_token)
        VALUES (?, ?, ?, ?, ?)
        ;
      """
      val stmt = conn.prepareStatement(sql)

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
  }

  // TODO: This should be in a separate, smaller table so that
  // looking through it is faster and no DISTINCT is necessary.
  // See MemoryState for guidance.

  /** Returns the segment-specific doc-ids that correspond
   *  to lucene documents that contain a mention with the
   *  specified label
   */
  def getDocIds(docBase: Int, label: String): Array[Int] = {
    using(ds.getConnection()) { conn =>
      val sql = s"""
        SELECT DISTINCT doc_id
        FROM mentions_$index
        WHERE doc_base=? AND label=?
        ORDER BY doc_id
        ;
      """
      val stmt = conn.prepareStatement(sql)
      stmt.setInt(1, docBase)
      stmt.setString(2, label)
      val results = stmt.executeQuery()
      val docIds = ArrayBuffer.empty[Int]
      while (results.next()) {
        docIds += results.getInt("doc_id")
      }
      docIds.toArray
    }
  }

  def getMatches(
    docBase: Int,
    docId: Int,
    label: String
  ): Array[(Int, Int)] = {
    using(ds.getConnection()) { conn =>
      val sql = s"""
        SELECT start_token, end_token
        FROM mentions_$index
        WHERE doc_base=? AND doc_id=? AND label=?
        ORDER BY start_token, end_token
        ;
      """
      val stmt = conn.prepareStatement(sql)
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
  }

  override def clear(): Unit = {
    delete()
  }

  /** delete all mentions from the state */
  // See https://examples.javacodegeeks.com/core-java/sql/delete-all-table-rows-example/.
  // "TRUNCATE is faster than DELETE since it does not generate rollback information and does not
  // fire any delete triggers."
  protected def delete(): Unit = {
    using(ds.getConnection()) { conn =>
      val sql = s"""DELETE FROM mentions_$index;""" // TODO test TRUNCATE
      conn.createStatement().executeUpdate(sql)
    }
  }

  /** delete all mentions with the provided label */
  def delete(label: String): Unit = {
    using(ds.getConnection()) { conn =>
      val sql = s"""DELETE FROM mentions_$index WHERE label=?;"""
      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, label)
      stmt.executeUpdate()
    }
  }

}
