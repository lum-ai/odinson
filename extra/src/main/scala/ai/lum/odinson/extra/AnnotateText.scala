package ai.lum.odinson.extra

import java.io.File

import org.clulab.processors.{ Document, Processor }
import org.clulab.processors.clu.{ BioCluProcessor, CluProcessor }
import org.clulab.serialization.json._
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.ConfigFactory
import ai.lum.nxmlreader.{ NxmlDocument, NxmlReader }
import org.clulab.utils.ScienceUtils
import ai.lum.common.Serializer
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._

import scala.util.{ Failure, Success, Try }
//import ai.lum.common.Serializer

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

  val scienceUtils = new ScienceUtils

  def preprocessText(text:String):String = {
    val textWithoutUnicode = scienceUtils.replaceUnicodeWithAscii(text)
    val textWithoutBibRefs = scienceUtils.removeBibRefs(textWithoutUnicode)
    val textWithoutFigRefs = scienceUtils.removeFigureAndTableReferences(textWithoutBibRefs)
    textWithoutFigRefs
  }

  val nxmlReader = new NxmlReader(
    sectionsToIgnore =  Set("references", "materials", "materials|methods", "methods", "supplementary-material"),
    ignoreFloats = true,
    transformText = preprocessText
  )

  processor.annotate("this") // load all required models

  def annotateNxmlFile(f: File): Document = {
    val nxmlDoc = nxmlReader.read(f)
    val docId: String = nxmlDoc match {
      case hasDoi if hasDoi.doi.nonEmpty => hasDoi.doi
      case hasPmcId if hasPmcId.pmc.nonEmpty => hasPmcId.pmc
      case _ => f.getName
    }
    // FIXME: consider creating separate docs for each piece later
    val contents = (nxmlDoc.paperAbstract + "\n\n" + nxmlDoc.paperBody).trim
    val doc = processor.annotate(contents)
    doc.id  = Some(docId)
    doc
  }

  def annotateTextFile(f: File): Document = {
    val text = f.readString()
    val doc = processor.annotate(text)
    // use file base name as document id
    doc.id = Some(f.getBaseName())
    doc
  }

  // NOTE parses the documents in parallel
  for (f <- textDir.listFilesByRegex(pattern = ".*\\.(txt|nxml)$", caseSensitive = false, recursive = true).toSeq.par) {
    //val docFile = new File(docsDir, f.getBaseName() + ".json")
    val docFile = new File(docsDir, f.getBaseName() + ".ser")
    if (docFile.exists()) {
      logger.warn(s"${docFile.getCanonicalPath} already exists")
    } else {
      Try {
        val doc = f.getName.toLowerCase match {
          case nxml if nxml.endsWith(".nxml") =>
            annotateNxmlFile(f)
          case _ =>
            annotateTextFile(f)
        }
        //doc.saveJSON(docFile, pretty = true)
        Serializer.serialize[Document](doc, docFile)
      } match {
        case Success(_) =>
          logger.info(s"Annotated ${f.getCanonicalPath}")
        case Failure(e) =>
          logger.error(s"Failed to process ${f.getName}", e)
      }

    }
  }

}
