package ai.lum.odinson.extra

import java.io._
import scala.util.{ Try, Success, Failure }
import com.typesafe.scalalogging.LazyLogging
import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.odinson.{ Document, Field, OdinsonIndexWriter, StringField }

object IndexDocuments extends App with LazyLogging {

  val config = ConfigFactory.load()
  val docsDir = config[File]("odinson.docsDir")
  val synchronizeOrderWithDocumentId = config[Boolean]("odinson.index.synchronizeOrderWithDocumentId")

  val writer = OdinsonIndexWriter.fromConfig()

  logger.info("Gathering documents")

  val documentFiles =
    if (synchronizeOrderWithDocumentId) {
      // files ordered by the id of the document
      docsDir.listFilesByWildcard("*.json", recursive = true)
        .map(f => (Document.fromJson(f).id.toInt, f))
        .toSeq
        .sortBy(_._1)
        .map(_._2)
    } else {
      docsDir.listFilesByWildcard("*.json", recursive = true)
        .toSeq.par
    }

  logger.info("Indexing documents")

  // index documents
  for (f <- documentFiles) {
    Try {
      val origDoc = Document.fromJson(f)
      // keep track of file name to retrieve sentence JSON,
      // but ignore the path to the docs directory to avoid issues encountered when moving `odinson.dataDir`.
      // NOTE: this assumes all files are located immediately under `odinson.docsDir`
      // With large document collections, it may be necessary to split documents across many subdirectories
      // To avoid peformance issues and limitations of certain file systems (ex. FAT32, ext2, etc.)
      val fileField: Field = StringField(name = "fileName", string = f.getName, store = true)
      val doc = origDoc.copy(metadata = origDoc.metadata ++ Seq(fileField))
      val block = writer.mkDocumentBlock(doc)
      writer.addDocuments(block)
    } match {
      case Success(_) =>
        logger.info(s"Indexed ${f.getName}")
      case Failure(e) =>
        logger.error(s"Failed to index ${f.getName}", e)
    }

  }

  writer.close
  // fin

}
