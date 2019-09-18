package ai.lum.odinson.extra

import java.io._
import scala.collection.GenSeq
import scala.util.{ Try, Success, Failure }
import com.typesafe.scalalogging.LazyLogging
import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.odinson.{ Document, OdinsonIndexWriter }

object IndexDocuments extends App with LazyLogging {

  val config = ConfigFactory.load()
  val docsDir = config[File]("odinson.docsDir")
  val synchronizeOrderWithDocumentId = config[Boolean]("odinson.index.synchronizeOrderWithDocumentId")

  val writer = OdinsonIndexWriter.fromConfig()

  // a sequence of (File, Document) tuples that *may* be parallel
  val filesAndDocs: GenSeq[(File, Document)] =
    if (synchronizeOrderWithDocumentId) {
      // files ordered by the id of the document
      docsDir.listFilesByWildcard("*.json", recursive = true)
        .map(f => (f, Document.fromJson(f)))
        .toSeq
        // NOTE we convert to int to preserve functionality added in PR #37
        // but maybe we should sort lexicographically instead
        .sortBy(_._2.id.toInt)
    } else {
      docsDir.listFilesByWildcard("*.json", recursive = true)
        .map(f => (f, Document.fromJson(f)))
        .toSeq.par // make parallel sequence
    }

  // index documents
  for (fileWithDoc <- filesAndDocs) {
    val (file, doc) = fileWithDoc
    Try {
      val block = writer.mkDocumentBlock(doc)
      writer.addDocuments(block)
    } match {
      case Success(_) =>
        logger.info(s"Indexed ${file.getName}")
      case Failure(e) =>
        logger.error(s"Failed to index ${file.getName}", e)
    }

  }

  writer.close
  // fin

}
