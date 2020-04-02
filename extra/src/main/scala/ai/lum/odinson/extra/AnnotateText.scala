package ai.lum.odinson.extra

import java.io.File
import scala.util.{ Failure, Success, Try }
import com.typesafe.scalalogging.LazyLogging
import org.clulab.processors.Processor
import org.clulab.processors.clu.{ BioCluProcessor, CluProcessor }
import ai.lum.common.FileUtils._
import ai.lum.common.ConfigUtils._
import ai.lum.common.ConfigFactory
import ai.lum.odinson.Document


object AnnotateText extends App with LazyLogging {

  val config = ConfigFactory.load()
  val textDir: File = config[File]("odinson.textDir")
  val docsDir: File = config[File]("odinson.docsDir")
  val processorType = config[String]("odinson.extra.processorType")

  val processor: Processor = processorType match {
    case "CluProcessor" => new CluProcessor
    case "BioCluProcessor" => new BioCluProcessor
  }

  // create output directory if it does not exist
  if (!docsDir.exists) {
    logger.warn(s"Making directory $docsDir")
    docsDir.mkdirs()
  }

  processor.annotate("this") // load all required models

  def annotateTextFile(f: File): Document = {
    val text = f.readString()
    val doc = processor.annotate(text)
    // use file base name as document id
    doc.id = Some(f.getBaseName())
    ProcessorsUtils.convertDocument(doc)
  }

  // NOTE parses the documents in parallel
  for (f <- textDir.listFilesByWildcard("*.txt", caseInsensitive = true, recursive = true).par) {
    val docFile = new File(docsDir, f.getBaseName() + ".json")

    if (docFile.exists) {
      logger.warn(s"${docFile.getCanonicalPath} already exists")
    } else {
      Try {
        val doc = annotateTextFile(f)
        docFile.writeString(doc.toJson)
      } match {
        case Success(_) =>
          logger.info(s"Annotated ${f.getCanonicalPath}")
        case Failure(e) =>
          logger.error(s"Failed to process ${f.getName}", e)
      }
    }
  }

}
