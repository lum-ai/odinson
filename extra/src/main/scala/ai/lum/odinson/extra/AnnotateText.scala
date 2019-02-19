package ai.lum.odinson.extra

import java.io.File
import org.clulab.processors.{ Document, Processor }
import org.clulab.processors.clu.{ CluProcessor, BioCluProcessor }
import org.clulab.serialization.json._
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.ConfigFactory
import ai.lum.common.Serializer
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
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

  processor.annotate("this") // load all required models

  // NOTE parses the documents in parallel
  for (f <- textDir.listFilesByWildcard("*.txt").toSeq.par) {
    //val docFile = new File(docsDir, f.getBaseName() + ".json")
    val docFile = new File(docsDir, f.getBaseName() + ".ser")
    if (docFile.exists()) {
      logger.warn(s"${docFile.getCanonicalPath} already exists")
    } else {
      logger.info(s"annotating ${f.getCanonicalPath}")
      val text = f.readString()
      val doc = processor.annotate(text)
      // use file base name as document id
      doc.id = Some(f.getBaseName())
      //doc.saveJSON(docFile, pretty = true)
      Serializer.serialize[Document](doc, docFile)
    }
  }

}
