package ai.lum.odinson.extra

import java.io._
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
  val wildcards = Seq("*.json", "*.json.gz")

  logger.info(s"Gathering documents from $docsDir")

  val documentFiles =
    if (synchronizeOrderWithDocumentId) {
      // files ordered by the id of the document
      docsDir.listFilesByWildcards(wildcards, recursive = true)
        .map(f => (Document.fromJson(f).id.toInt, f))
        .toSeq
        .sortBy(_._1)
        .map(_._2)
    } else {
      docsDir.listFilesByWildcards(wildcards, recursive = true)
        .toSeq.par
    }

  logger.info("Indexing documents")

  // index documents
  for (f <- documentFiles) {
    Try {
      val doc = Document.fromJson(f)
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
