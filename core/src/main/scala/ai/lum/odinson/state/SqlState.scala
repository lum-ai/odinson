package ai.lum.odinson.state

import java.sql.Connection
import java.util.concurrent.atomic.AtomicLong

import scala.collection.mutable.ArrayBuffer
import ai.lum.common.ConfigUtils._
import ai.lum.common.TryWithResources.using
import ai.lum.odinson.NamedCapture
import ai.lum.odinson.OdinsonMatch
import ai.lum.odinson.StateMatch
import com.typesafe.config.Config
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

// See https://dzone.com/articles/jdbc-what-resources-you-have about closing things.
class IdProvider(protected var id: Int = 0) {

  def next: Int = {
    val result = id

    id += 1
    result
  }
}

abstract class WriteNode(val odinsonMatch: OdinsonMatch, idProvider: IdProvider) {
  val childNodes: Array[WriteNode] = {
    odinsonMatch.namedCaptures.map { namedCapture =>
      new OdinsonMatchWriteNode(namedCapture.capturedMatch, this, namedCapture, idProvider)
    }
  }
  val id: Int = idProvider.next

  def label: String
  def name: String
  def parentNodeOpt: Option[WriteNode]

  def flatten(writeNodes: ArrayBuffer[WriteNode]): Unit = {
    childNodes.foreach(_.flatten(writeNodes))
    writeNodes += this
  }

  def parentId: Int = parentNodeOpt.map(_.id).getOrElse(-1)

  def start: Int = odinsonMatch.start

  def end: Int = odinsonMatch.end
}

class ResultItemWriteNode(val resultItem: ResultItem, idProvider: IdProvider) extends WriteNode(resultItem.odinsonMatch, idProvider) {

  def label: String = resultItem.label

  def name: String = resultItem.name

  def parentNodeOpt: Option[WriteNode] = None
}

class OdinsonMatchWriteNode(odinsonMatch: OdinsonMatch, parentNode: WriteNode, val namedCapture: NamedCapture, idProvider: IdProvider) extends WriteNode(odinsonMatch, idProvider) {

  def label: String = namedCapture.label.getOrElse("")

  def name: String = namedCapture.name

  val parentNodeOpt: Option[WriteNode] = Some(parentNode)
}

case class ReadNode(docIndex: Int, name: String, id: Int, parentId: Int, childCount: Int, childLabel: String, start: Int, end: Int)

object SqlResultItem {

  def toWriteNodes(resultItem: ResultItem, idProvider: IdProvider): IndexedSeq[WriteNode] = {
    val arrayBuffer = new ArrayBuffer[WriteNode]()

    new ResultItemWriteNode(resultItem, idProvider).flatten(arrayBuffer)
    arrayBuffer.toIndexedSeq
  }

  def fromReadNodes(docBase: Int, docId: Int, label: String, readItems: ArrayBuffer[ReadNode]): ResultItem = {
    val iterator = readItems.reverseIterator
    val first = iterator.next

    def findNamedCaptures(childCount: Int): Array[NamedCapture] = {
      val namedCaptures = if (childCount == 0) Array.empty[NamedCapture] else new Array[NamedCapture](childCount)
      var count = 0

      while (count < childCount) {
        val readNode = iterator.next

        count += 1
        // These go in backwards because of reverse.
        namedCaptures(childCount - count) = NamedCapture(readNode.name, if (readNode.childLabel.nonEmpty) Some(readNode.childLabel) else None,
          StateMatch(readNode.start, readNode.end, findNamedCaptures(readNode.childCount)))
      }
      namedCaptures
    }

    ResultItem(docBase, docId, first.docIndex, label, first.name,
      StateMatch(first.start, first.end, findNamedCaptures(first.childCount)))
  }
}

class SqlState(val connection: Connection, protected val factoryIndex: Long, protected val stateIndex: Long) extends State {
  protected val lastId = 0; // Increment before use.

  init()

  def init(): Unit = {
    createTable()
    createIndexes()
  }

  def createTable(): Unit = {
    val sql = s"""
      CREATE TABLE IF NOT EXISTS mentions_${factoryIndex}_$stateIndex (
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
      val sql =
        s"""
          CREATE INDEX IF NOT EXISTS mentions_${factoryIndex}_${stateIndex}_index
          ON mentions_${factoryIndex}_$stateIndex(doc_base, doc_id, label);
        """
      using(connection.createStatement()) { statement =>
        statement.executeUpdate(sql)
      }
    }

    {
      val sql =
        s"""
          CREATE INDEX IF NOT EXISTS docIds_${factoryIndex}_${stateIndex}_index
          ON mentions_${factoryIndex}_$stateIndex(doc_base, label);
        """
      using(connection.createStatement()) { statement =>
        statement.executeUpdate(sql)
      }
    }
  }

  // Reuse the same connection and prepared statement.
  // TODO Group the mentions and insert multiple at a time.
  // TODO Also pass in the number of items, perhaps how many of each kind?
  override def addMentions(resultItems: Iterator[ResultItem]): Unit = {
    val sql = s"""
      INSERT INTO mentions_${factoryIndex}_$stateIndex
        (doc_base, doc_id, doc_index, label, name, id, parent_id, child_count, child_label, start_token, end_token)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ;
    """
    using(connection.prepareStatement(sql)) { preparedStatement =>
      val dbSetter = DbSetter(preparedStatement)
      val idProvider = new IdProvider()

      // TODO this should be altered to add several mentions in a single call
      resultItems.foreach { resultItem =>
        val stateNodes = SqlResultItem.toWriteNodes(resultItem, idProvider)

//        println(resultItem) // debugging

        stateNodes.foreach { stateNode =>
          dbSetter
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
              .get
              .executeUpdate()
        }
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
  override def getDocIds(docBase: Int, label: String): Array[Int] = {
    val sql = s"""
      SELECT DISTINCT doc_id
      FROM mentions_${factoryIndex}_$stateIndex
      WHERE doc_base=? AND label=?
      ORDER BY doc_id
      ;
    """
    using(connection.prepareStatement(sql)) { preparedStatement =>
      val resultSet = DbSetter(preparedStatement)
          .setNext(docBase)
          .setNext(label)
          .get
          .executeQuery()

      DbGetter(resultSet).map { dbGetter =>
        dbGetter.getInt
      }.toArray
    }
  }

  override def getMatches(docBase: Int, docId: Int, label: String): Array[ResultItem] = {
    val sql = s"""
      SELECT doc_index, name, id, parent_id, child_count, child_label, start_token, end_token
      FROM mentions_${factoryIndex}_$stateIndex
      WHERE doc_base=? AND doc_id=? AND label=?
      ORDER BY id
      ;
    """
    using(connection.prepareStatement(sql)) { preparedStatement =>
      val resultSet = new DbSetter(preparedStatement)
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
    using(connection.createStatement()) { statement =>
      statement.executeUpdate(sql)
    }
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
    val jdbcUrl = config[String]("state.sql.url")
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
