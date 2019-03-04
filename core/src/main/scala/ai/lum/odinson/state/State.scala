package ai.lum.odinson.state

import scala.collection.mutable.ArrayBuffer
import org.h2.jdbcx.JdbcDataSource

class State {

  val dataSource = new JdbcDataSource()
  dataSource.setURL("jdbc:h2:mem:odinson")
  val connection = dataSource.getConnection()
  val statement = connection.createStatement()

  def close(): Unit = {
    statement.close()
    connection.close()
  }

  def init(): Unit = {
    val sql = """CREATE TABLE mentions (
                |  id BIGINT AUTO_INCREMENT,
                |  label VARCHAR(50) NOT NULL,
                |  luceneDocId INT NOT NULL,
                |  startToken INT NOT NULL,
                |  endToken INT NOT NULL,
                |);""".stripMargin
    statement.executeUpdate(sql)
  }

  def addMention(
    label: String,
    luceneDocId: Int,
    startToken: Int,
    endToken: Int
  ): Unit = {
    val sql = s"""INSERT INTO mentions (
                 |  label,
                 |  luceneDocId,
                 |  startToken,
                 |  endToken,
                 |) VALUES (
                 |  '$label',
                 |  $luceneDocId,
                 |  $startToken,
                 |  $endToken,
                 |);""".stripMargin
    statement.executeUpdate(sql)
  }

  def getMentions(label: String, luceneDocId: Int) = {
    val sql = s"""SELECT *
                 |FROM mentions
                 |WHERE label='$label' AND luceneDocId=$luceneDocId
                 |;""".stripMargin
    val results = statement.executeQuery(sql)
    val mentions = ArrayBuffer.empty[(String, Int, Int, Int)]
    while (results.next()) {
      val mention = (
        results.getString("label"),
        results.getInt("luceneDocId"),
        results.getInt("startToken"),
        results.getInt("endToken"),
      )
      mentions += mention
    }
    mentions.toArray
  }

}
