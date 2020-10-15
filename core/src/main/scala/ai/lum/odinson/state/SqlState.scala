package ai.lum.odinson.state

import java.io.File
import java.io.StringReader
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicLong

import scala.collection.mutable.ArrayBuffer
import ai.lum.common.ConfigUtils._
import ai.lum.common.TryWithResources.using
import ai.lum.odinson.lucene.search.OdinsonIndexSearcher
import ai.lum.odinson.{IdGetter, LazyIdGetter, Mention, MentionFactory, NamedCapture, OdinsonMatch, StateMatch}
import com.typesafe.config.Config
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.apache.lucene.search.IndexSearcher

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

  override def toString(): String = s"""${getClass.getSimpleName}(name = "$name", id = $id, parentId = $parentId, length = ${childNodes.length}, label = "$label", start = $start, end = $end)"""
}

class MentionWriteNode(val mention: Mention, idProvider: IdProvider) extends WriteNode(mention.odinsonMatch, idProvider) {

  def label: String = mention.label.getOrElse("")

  def name: String = mention.foundBy

  def parentNodeOpt: Option[WriteNode] = None
}

class OdinsonMatchWriteNode(odinsonMatch: OdinsonMatch, parentNode: WriteNode, val namedCapture: NamedCapture, idProvider: IdProvider) extends WriteNode(odinsonMatch, idProvider) {

  def label: String = namedCapture.label.getOrElse("")

  def name: String = namedCapture.name

  val parentNodeOpt: Option[WriteNode] = Some(parentNode)
}

case class ReadNode(docIndex: Int, name: String, id: Int, parentId: Int, childCount: Int, childLabel: String, start: Int, end: Int)

object SqlResultItem {

  def toWriteNodes(mention: Mention, idProvider: IdProvider): IndexedSeq[WriteNode] = {
    val arrayBuffer = new ArrayBuffer[WriteNode]()

    new MentionWriteNode(mention, idProvider).flatten(arrayBuffer)
    arrayBuffer.toIndexedSeq
  }

  def fromReadNodes(docBase: Int, docId: Int, label: Option[String], readItems: ArrayBuffer[ReadNode], mentionFactory: MentionFactory, idGetter: IdGetter): Mention = {
    val iterator = readItems.reverseIterator
    val first = iterator.next

    def findNamedCaptures(childCount: Int): Array[NamedCapture] = {
      val namedCaptures = if (childCount == 0) Array.empty[NamedCapture] else new Array[NamedCapture](childCount)
      var count = 0

      while (count < childCount) {
        val readNode = iterator.next

        count += 1
        // These go in backwards because of reverse.
        namedCaptures(childCount - count) = NamedCapture(
          readNode.name,
          if (readNode.childLabel.nonEmpty) Some(readNode.childLabel) else None,
          StateMatch(readNode.start, readNode.end, findNamedCaptures(readNode.childCount)))
      }
      namedCaptures
    }

    mentionFactory.newMention(
      StateMatch(first.start, first.end, findNamedCaptures(first.childCount)),
      label,
      first.docIndex, // luceneDocId
      docId,          // luceneSegmentDocId
      docBase,        // luceneSegmentDocBase
      idGetter,
      first.name,     // foundBy
      )
  }
}

// See https://dzone.com/articles/jdbc-what-resources-you-have about closing things.
class SqlState protected (val connection: Connection, protected val factoryIndex: Long, protected val stateIndex: Long, val persistOnClose: Boolean = false, val persistFile: Option[File] = None, mentionFactory: MentionFactory, indexSearcher: OdinsonIndexSearcher) extends State {

  if (persistOnClose) require(persistFile.isDefined)

  protected val mentionsTable = s"mentions_${factoryIndex}_$stateIndex"
  protected val idProvider = new IdProvider()
  protected var closed = false

  create()

  def create(): Unit = {
    createTable()
    createIndexes()
  }

  def createTable(): Unit = {
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

  // Reuse the same connection and prepared statement.
  // TODO Group the mentions and insert multiple at a time.
  // TODO Also pass in the number of items, perhaps how many of each kind?
  // TODO Make this a single transaction.
  override def addMentions(mentions: Iterator[Mention]): Unit = {
    val sql = s"""
      INSERT INTO $mentionsTable
        (doc_base, doc_id, doc_index, label, name, id, parent_id, child_count, child_label, start_token, end_token)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ;
    """
    using(connection.prepareStatement(sql)) { preparedStatement =>
      val dbSetter = DbSetter(preparedStatement)

      // TODO this should be altered to add several mentions in a single call
      mentions.foreach { mention =>
        val stateNodes = SqlResultItem.toWriteNodes(mention, idProvider)

//        println(resultItem) // debugging
        stateNodes.foreach { stateNode =>
//          println(stateNode) // debugging
          dbSetter
              .setNext(mention.luceneSegmentDocBase)
              .setNext(mention.luceneSegmentDocId)
              .setNext(mention.luceneDocId)
              .setNext(mention.label.getOrElse(""))
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
      FROM $mentionsTable
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

  override def getMentions(docBase: Int, docId: Int, label: String): Array[Mention] = {
    val sql = s"""
      SELECT doc_index, name, id, parent_id, child_count, child_label, start_token, end_token
      FROM $mentionsTable
      WHERE doc_base=? AND doc_id=? AND label=?
      ORDER BY id
      ;
    """
    using(connection.prepareStatement(sql)) { preparedStatement =>
      val mentionSet = new DbSetter(preparedStatement)
          .setNext(docBase)
          .setNext(docId)
          .setNext(label)
          .get
          .executeQuery()
      val readNodes = ArrayBuffer.empty[ReadNode]
      val mentions = ArrayBuffer.empty[Mention]

      DbGetter(mentionSet).foreach { dbGetter =>
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
          val idGetter = LazyIdGetter(indexSearcher, docId)
          mentions += SqlResultItem.fromReadNodes(docBase, docId, Some(label), readNodes, mentionFactory, idGetter)
          readNodes.clear()
        }
      }
      mentions.toArray
    }
  }

  override def getAllMentions(): Iterator[Mention] = {
    // TODO: Keith
    ???
  }

  override def clear(): Unit = {
    drop()
    create()
  }

  def persist(file: File): Unit = {
    val path = file.getPath
    val sql = s"""
      SCRIPT TO '$path'
      ;
    """
    using(connection.prepareStatement(sql)) { preparedStatement =>
      preparedStatement.execute()
    }
  }

  override def close(): Unit = {
    if (persistOnClose)
      persist(persistFile.get)

    if (!closed) {
      try {
        drop()
      }
      finally {
        closed = true
        connection.close()
      }
    }
  }

  // See https://examples.javacodegeeks.com/core-java/sql/delete-all-table-rows-example/.
  // "TRUNCATE is faster than DELETE since it does not generate rollback information and does not
  // fire any delete triggers."  There's also no need to update indexes.
  // However, DROP is what we want.  The tables and indexes should completely disappear.
  protected def drop(): Unit = {
    val sql = s"""
      DROP TABLE $mentionsTable
      ;
    """
    using(connection.createStatement()) { statement =>
      statement.executeUpdate(sql)
    }
  }
}


object SqlState {
  protected var count: AtomicLong = new AtomicLong

  def apply(config: Config, indexSearcher: OdinsonIndexSearcher): SqlState = {
    val persistOnClose = config[Boolean]("state.sql.persistOnClose")
    val stateFile = config.get[File]("state.sql.persistFile")
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

    val mentionFactory = MentionFactory.fromConfig(config)
    new SqlState(dataSource.getConnection, count.getAndIncrement, count.getAndIncrement, persistOnClose, stateFile, mentionFactory, indexSearcher)
  }

}
