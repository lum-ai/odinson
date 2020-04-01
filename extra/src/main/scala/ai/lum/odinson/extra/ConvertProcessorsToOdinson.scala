package ai.lum.odinson.extra

import java.io.File
import scala.util.{ Try, Success, Failure }
import com.typesafe.scalalogging.LazyLogging
import org.clulab.serialization.json.JSONSerializer
import ai.lum.common.ConfigUtils._
import ai.lum.common.ConfigFactory
import ai.lum.common.FileUtils._
import ai.lum.odinson.Document

object ConvertProcessorsToOdinson extends App with LazyLogging {

  val config = ConfigFactory.load()
  val docsDir = config[File]("odinson.docsDir")
  val procDir = config[File]("odinson.procDir")

  logger.info(s"processors documents at $procDir")
  logger.info(s"odinson documents at $docsDir")

  for (f <- procDir.listFilesByWildcard("*.json", recursive = true).par) {
    Try {
      val newFile = new File(docsDir, f.getName + ".gz")
      val processorsDoc = JSONSerializer.toDocument(f)
      val odinsonDoc = ProcessorsUtils.convertDocument(processorsDoc)
      newFile.writeString(odinsonDoc.toJson)
    } match {
      case Success(_) => logger.info(s"converted ${f.getName}")
      case Failure(e) => logger.error(s"failed to convert ${f.getName}", e)
    }
  }

}
