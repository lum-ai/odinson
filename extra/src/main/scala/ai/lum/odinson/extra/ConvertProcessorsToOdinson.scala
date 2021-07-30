package ai.lum.odinson.extra

import java.io.File

import scala.util.{Failure, Success, Try}
import com.typesafe.scalalogging.LazyLogging
import org.clulab.serialization.json.JSONSerializer
import ai.lum.common.ConfigUtils._
import ai.lum.common.ConfigFactory
import ai.lum.common.FileUtils._
import ai.lum.odinson.Document
import ai.lum.odinson.extra.utils.{ExtraFileUtils, ProcessorsUtils}

object ConvertProcessorsToOdinson extends App with LazyLogging {

  val config = ConfigFactory.load()
  val docsDir = config.apply[File]("odinson.docsDir")
  val procDir = config.apply[File]("odinson.procDir")
  val extension = config.apply[String]("odinson.procExtension")

  logger.info(s"processors documents at $procDir")
  logger.info(s"odinson documents at $docsDir")

  for (f <- procDir.listFilesByWildcard(s"*$extension", recursive = true).par) {
    Try {
      val newFile = ExtraFileUtils.resolveFileWithNewExtension(f, procDir, docsDir, "gz")
      if (!newFile.exists) {
        val processorsDoc = JSONSerializer.toDocument(f.readString())
        val odinsonDoc = ProcessorsUtils.convertDocument(processorsDoc)
        newFile.writeString(odinsonDoc.toJson)
      } else {
        logger.info(s"skipping ${f.getName} because it was already processed")
      }
    } match {
      case Success(_) => logger.info(s"converted ${f.getName}")
      case Failure(e) => logger.error(s"failed to convert ${f.getName}", e)
    }
  }

}
