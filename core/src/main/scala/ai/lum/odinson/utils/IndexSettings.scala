package ai.lum.odinson.utils

import java.io.IOException

import ai.lum.common.TryWithResources.using
import ai.lum.odinson.OdinsonIndexWriter
import org.apache.lucene.store.{ Directory, IOContext }
import ujson.Value

/** Class to store settings for the OdinsonIndex.
  * Currently included:
  *
  * @param storedFields the names of the fields that are stored fields in the lucene index
  */
class IndexSettings(val sentenceStoredFields: Seq[String], val metadataStoredFields: Seq[String]) {

  // All stored fields
  val storedFields = sentenceStoredFields ++ metadataStoredFields
  def asJsonValue: Value = {
    ujson.Obj(
      "sentenceStoredFields" -> sentenceStoredFields,
      "metadataStoredFields" -> metadataStoredFields
    )
  }

  def dump: String = {
    ujson.write(asJsonValue, indent = 4)
  }

}

object IndexSettings {

  def apply(sentenceStoredFields: Seq[String], metadataStoredFields: Seq[String]): IndexSettings = new IndexSettings(sentenceStoredFields, metadataStoredFields)

  def load(dump: String): IndexSettings = {
    val json = ujson.read(dump)
    val sentenceStoredFields = json("sentenceStoredFields").arr.map(_.str)
    val metadataStoredFields = json("metadataStoredFields").arr.map(_.str)
    new IndexSettings(sentenceStoredFields, metadataStoredFields)
  }

  def fromDirectory(directory: Directory): IndexSettings =
    try {
      using(directory.openInput(OdinsonIndexWriter.SETTINGSINFO_FILENAME, new IOContext)) {
        stream =>
          IndexSettings.load(stream.readString())
      }
    } catch {
      case e: IOException => IndexSettings(Seq(), Seq())
    }

}
