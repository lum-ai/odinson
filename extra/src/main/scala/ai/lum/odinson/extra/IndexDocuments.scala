package ai.lum.odinson.extra

import java.io._
import scala.util.{Try, Success, Failure}
import com.typesafe.scalalogging.LazyLogging

import ai.lum.common.ConfigFactory
import com.typesafe.config.{Config, ConfigValueFactory}

import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.odinson.{Document, Field, OdinsonIndexWriter, StringField}

import scala.collection.GenIterable

object IndexDocuments extends App with LazyLogging {
  //
  var config = ConfigFactory.load()
  // inject a new arg for the folder
  if (args.length == 1) {
    logger.info(s"Received dataDir as a parameter <${args(0)}>")
    // receive the path from the arguments
    config = config
      .withValue("odinson.dataDir", ConfigValueFactory.fromAnyRef(args(0)))
      // re-compute the index and docs path's
      .withValue(
        "odinson.indexDir",
        ConfigValueFactory.fromAnyRef(args(0) + "/index")
      )
      .withValue(
        "odinson.docsDir",
        ConfigValueFactory.fromAnyRef(args(0) + "/docs")
      )
  }
  //
  val docsDir = config[File]("odinson.docsDir")
  val synchronizeOrderWithDocumentId =
    config[Boolean]("odinson.index.synchronizeOrderWithDocumentId")
  //
  val writer = OdinsonIndexWriter.fromConfig(config.getConfig("odinson"))
  val wildcards = Seq("*.json", "*.json.gz")
  logger.info(s"Gathering documents from $docsDir")
  // make this a function
  val documentFiles =
    if (synchronizeOrderWithDocumentId) {
      // files ordered by the id of the document
      docsDir
        .listFilesByWildcards(wildcards, recursive = true)
        .map(f => (Document.fromJson(f).id.toInt, f))
        .toSeq
        .sortBy(_._1)
        .map(_._2)
    } else {
      docsDir
        .listFilesByWildcards(wildcards, recursive = true)
        .par
    }
  // ^ this part should be a function
  logger.info("Indexing documents")
  indexDocuments(writer, documentFiles)
  writer.close
  // fin
  // Note that documentFiles may or may not be parallel, hence the GenIterable
  def indexDocuments(
      writer: OdinsonIndexWriter,
      documentFiles: GenIterable[File]
  ): Unit = {
    // index documents
    for (f <- documentFiles) {
      Try {
        val origDoc = Document.fromJson(f)
        // keep track of file name to retrieve sentence JSON,
        // but ignore the path to the docs directory to avoid issues encountered when moving `odinson.dataDir`.
        // NOTE: this assumes all files are located immediately under `odinson.docsDir`
        // With large document collections, it may be necessary to split documents across many subdirectories
        // To avoid performance issues and limitations of certain file systems (ex. FAT32, ext2, etc.)
        val fileField: Field =
          StringField(name = "fileName", string = f.getName, store = true)
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
  }
}
