package ai.lum.odinson.extra

import java.io.File

import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.odinson.extra.utils.ExtraFileUtils
import ai.lum.odinson.{Document, MetadataWrapper}
import com.typesafe.scalalogging.LazyLogging

/**
  * App for adding metadata into the Odinson Documents that have already been created.
  * The metadata files should:
  *   (a) contain the id of the Odinson Document that they correspond to
  *   (b) be in a compatible json format (using ai.lum.odinson.MetadataWrapper is a good
  *       way to ensure this)
  *
  *   The metadata can be added in place or new Documents can be written.  Also,
  *   any existing metadata can be replaced or appended to.  These options can be selected
  *   in the config.
  */
object AddMetadataToDocuments extends LazyLogging {

  def main(args: Array[String]): Unit = {
    // run with no overriding of the default config
    usingConfigSettings(Map.empty)
  }

  def usingConfigSettings(settings: Map[String, Any]): Unit = {
    val config = ConfigFactory.load(directSettings = settings)
    val docsDir = config.apply[File]("metadata.originalDocsDir")
    val metadataDir = config.apply[File]("metadata.metadataDir")
    val metadataExtension = config.apply[String]("metadata.metadataExt") // json format supported
    val outputDir = config.apply[File]("metadata.finalDocsDir")
    val appendMetadata: Boolean = config.apply[Boolean]("metadata.append") // vs replace/overwrite

    val metadataFiles = metadataDir.listFilesByWildcard(s"*$metadataExtension", recursive = true)
    val originalDocFiles = docsDir.listFilesByWildcards(odinsonDocsWildcards, recursive = true)

    // make a map from docid to the sequence of metadata Files that correspond to that docid
    val id2MetadataFiles = alignFilenamesToIds(metadataFiles.toVector)

    for (docFile <- originalDocFiles) {
      val doc = Document.fromJson(docFile.readString())
      // get all the fields from all metadata files that correspond to that docid
      val metadataFields = id2MetadataFiles.getOrElse(doc.id, Seq.empty)
        .flatMap(mf => MetadataWrapper.fromJson(mf.readString()).fields)
      // update the doc metadata with the new metadata
      val updated = doc.addMetadata(metadataFields, appendMetadata)
      // make the file in the correct place for the updated doc
      val outfile = ExtraFileUtils.resolveFile(docFile, docsDir, outputDir)
      // write!
      outfile.writeString(updated.toJson)

    }
  }


  def alignFilenamesToIds(metadataFiles: Seq[File]): Map[String, Seq[File]] = {
    metadataFiles.groupBy { f =>
      val metadata = MetadataWrapper.fromJson(f.readString())
      metadata.id
    }
  }

}
