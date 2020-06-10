package ai.lum.odinson.state

import scala.collection.mutable.ArrayBuffer
import com.zaxxer.hikari.{ HikariConfig, HikariDataSource }
import ai.lum.common.TryWithResources.using

class HikariState(val url: String) extends State {

  private val config = new HikariConfig
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
  private val ds = new HikariDataSource(config)

  init()



  def close(): Unit = {
    ds.close()
  }

  def init(): Unit = {
    createTable()
    createIndex()
  }

  def createTable(): Unit = {
    using(ds.getConnection()) { conn =>
      val sql = """
        CREATE TABLE IF NOT EXISTS mentions (
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

  def createIndex(): Unit = {
    using(ds.getConnection()) { conn =>
      val sql = """
        CREATE INDEX IF NOT EXISTS mentions_index
        ON mentions(doc_base, doc_id, label);
      """
      conn.createStatement().executeUpdate(sql)
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
      // FIXME this should be altered to add several mentions in a single call
      val sql = """
        INSERT INTO mentions
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

  /** Returns the segment-specific doc-ids that correspond
   *  to lucene documents that contain a mention with the
   *  specified label
   */
  def getDocIds(docBase: Int, label: String): Array[Int] = {
    using(ds.getConnection()) { conn =>
      val sql = """
        SELECT DISTINCT doc_id
        FROM mentions
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
      val sql = """
        SELECT start_token, end_token
        FROM mentions
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

  /** delete all mentions from the state */
  def delete(): Unit = {
    using(ds.getConnection()) { conn =>
      val sql = "DELETE FROM mentions;"
      conn.createStatement().executeUpdate(sql)
    }
  }

  /** delete all mentions with the provided label */
  def delete(label: String): Unit = {
    using(ds.getConnection()) { conn =>
      val sql = "DELETE FROM mentions WHERE label=?;"
      val stmt = conn.prepareStatement(sql)
      stmt.setString(1, label)
      stmt.executeUpdate()
    }
  }

}
