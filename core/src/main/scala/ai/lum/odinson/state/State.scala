package ai.lum.odinson.state

import scala.collection.mutable.ArrayBuffer
import org.h2.jdbcx.JdbcDataSource

class State(val url: String) {

  private val dataSource = new JdbcDataSource()
  dataSource.setURL(url)
  private val connection = dataSource.getConnection()
  private val statement = connection.createStatement()

  def close(): Unit = {
    statement.close()
    connection.close()
  }

  def init(): Unit = {
    val sql = """
      CREATE TABLE mentions (
        doc_base INT NOT NULL,
        doc_id INT NOT NULL,
        label VARCHAR(50) NOT NULL,
        start_token INT NOT NULL,
        end_token INT NOT NULL,
      );
    """
    statement.executeUpdate(sql)
  }

  def addMention(
    docBase: Int,
    docId: Int,
    label: String,
    startToken: Int,
    endToken: Int
  ): Unit = {
    val sql = s"""
      INSERT INTO mentions
        (doc_base, doc_id, label, start_token, end_token)
      VALUES
        ($docBase, $docId, '$label', $startToken, $endToken)
      ;
    """
    statement.executeUpdate(sql)
  }

  /** Returns the segment-specific doc-ids that correspond
   *  to lucene documents that contain a mention with the
   *  specified label
   */
  def getDocIds(docBase: Int, label: String): Array[Int] = {
    val sql = s"""
      SELECT DISTINCT doc_id
      FROM mentions
      WHERE doc_base=$docBase
      AND label='$label'
      ORDER BY doc_id
      ;
    """
    val results = statement.executeQuery(sql)
    val docIds = ArrayBuffer.empty[Int]
    while (results.next()) {
      docIds += results.getInt("doc_id")
    }
    docIds.toArray
  }

  def getMatches(docBase: Int, docId: Int, label: String): Array[(Int, Int)] = {
    val sql = s"""
      SELECT start_token, end_token
      FROM mentions
      WHERE doc_base=$docBase
      AND doc_id=$docId
      AND label='$label'
      ORDER BY start_token, end_token
      ;
    """
    val results = statement.executeQuery(sql)
    val matches = ArrayBuffer.empty[(Int, Int)]
    while (results.next()) {
      val start = results.getInt("start_token")
      val end = results.getInt("end_token")
      matches += Tuple2(start, end)
    }
    matches.toArray
  }

}
