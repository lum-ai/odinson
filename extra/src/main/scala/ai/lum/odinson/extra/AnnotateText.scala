package ai.lum.odinson.extra

import java.io.File
import org.clulab.processors.clu.BioCluProcessor
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.common.Serializer

object AnnotateText extends App with LazyLogging {

  val config = ConfigFactory.load()
  val textDir = config[File]("odinson.textDir")
  val docsDir = config[File]("odinson.docsDir")

  val processor = new BioCluProcessor
  processor.annotate("this") // load all required models

  // NOTE parses the documents in parallel
  for (f <- textDir.listFilesByWildcard("*.txt").toSeq.par) {
    val docFile = new File(docsDir, f.getBaseName() + ".ser")
    if (docFile.exists()) {
      logger.info(s"${docFile.getCanonicalPath()} already exists")
    } else {
      logger.info(s"annotating ${f.getCanonicalPath()}")
      val text = f.readString()
      val doc = processor.annotate(text)
      // use file base name as document id
      doc.id = Some(f.getBaseName())
      Serializer.serialize(doc, docFile)
    }
  }

}
