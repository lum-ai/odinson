package ai.lum.odinson.extra

import java.io._
import scala.util.{ Try, Success, Failure }
import com.typesafe.scalalogging.LazyLogging
import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.odinson.{ Document, OdinsonIndexWriter }

object NewIndexDocuments extends App with LazyLogging {

  val config = ConfigFactory.load()
  val docsDir  = config[File]("odinson.docsDir")

  val writer = OdinsonIndexWriter.fromConfig

  // NOTE indexes the documents in parallel
  for {
    f <- docsDir.listFilesByWildcard("*.json", recursive = true).toSeq.par
  } {
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
