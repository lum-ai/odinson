package ai.lum.odinson.extra

import java.io.File

import org.clulab.processors.{ Document, Processor }
import org.clulab.processors.clu.{ BioCluProcessor, CluProcessor }
import org.clulab.serialization.json._
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.ConfigFactory
import ai.lum.nxmlreader.{ NxmlDocument, NxmlReader }
import org.clulab.utils.ScienceUtils
import ai.lum.labrador.{ Author, DateTime, DocumentMetadata, DOI, Organization, PMID }
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

  /**
    * Construct [[ai.lum.labrador.DocumentMetadata]] from an [[ai.lum.nxmlreader.NxmlDocument]].
    */
  def mkDocumentMetadata(nxmlDoc: NxmlDocument): Option[DocumentMetadata] = {

    val doi: Option[DOI] = Try { nxmlDoc.doi } match {
      case Success(doi) => Some(DOI(doi))
      case Failure(_)   => None
    }

    val pmid: Option[PMID] = Try { nxmlDoc.pmid } match {
      case Success(pmid) => Some(PMID(pmid))
      case Failure(_)    => None
    }

    val title: Option[String] = Try { nxmlDoc.title } match {
      case Success(paperTitle) => Some(paperTitle)
      case Failure(_)          => None
    }

    val authors: Seq[Author] = Try { nxmlDoc.authors } match {
      case Success(nxmlAuthors) =>
        nxmlAuthors.map{ nxmlAuthor =>
          Author(givenName = nxmlAuthor.givenNames, surName = nxmlAuthor.surname, email = None, affiliation = None)
        }
      case Failure(_)           => Nil
    }

    val pubDate: Option[DateTime] = Try { nxmlDoc.pubDate.head } match {
      case Success(nxmlPubDate) =>
        Some(DateTime(year = Some(nxmlPubDate.year), month = nxmlPubDate.month, day = nxmlPubDate.day))
      case Failure(_)           => None
    }

    val dm = DocumentMetadata(
      authors           = authors,
      topics            = Nil,
      title             = title,
      // FIXME: try to retrieve venue name from NXML?
      journal           = None,
      volume            = None,
      publicationDate   = pubDate,
      doi               = doi,
      pmid              = pmid,
      license           = None
    )
    Some(dm)
  }

  def annotateNxmlFile(f: File): (Document, Option[DocumentMetadata]) = {
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
    (doc, mkDocumentMetadata(nxmlDoc))
  }

  def annotateTextFile(f: File): (Document, Option[DocumentMetadata]) = {
    val text = f.readString()
    val doc = processor.annotate(text)
    // use file base name as document id
    doc.id = Some(f.getBaseName())
    (doc, None)
  }

  // NOTE parses the documents in parallel
  for (f <- textDir.listFilesByRegex(pattern = ".*\\.(txt|nxml)$", caseSensitive = false, recursive = true).toSeq.par) {
    //val docFile = new File(docsDir, f.getBaseName() + ".json")
    val docFile      = new File(docsDir, f.getBaseName() + ".ser")
    val metadataFile = new File(docsDir, f.getBaseName() + "metadata.ser")

    if (docFile.exists()) {
      logger.warn(s"${docFile.getCanonicalPath} already exists")
    } else {
      Try {
        val (doc, md) = f.getName.toLowerCase match {
          case nxml if nxml.endsWith(".nxml") =>
            annotateNxmlFile(f)
          case _ =>
            annotateTextFile(f)
        }
        //doc.saveJSON(docFile, pretty = true)
        Serializer.serialize[Document](doc, docFile)
        // serialize any document metadata
        if (md.nonEmpty) {
          Serializer.serialize[DocumentMetadata](md.get, metadataFile)
        }
      } match {
        case Success(_) =>
          logger.info(s"Annotated ${f.getCanonicalPath}")
        case Failure(e) =>
          logger.error(s"Failed to process ${f.getName}", e)
      }

    }
  }

}
