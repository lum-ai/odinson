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
class IndexSettings(val storedFields: Seq[String]) {

  def asJsonValue: Value = {
    ujson.Obj(
      "storedFields" -> storedFields
    )
  }

  def dump: String = {
    ujson.write(asJsonValue, indent = 4)
  }

}

object IndexSettings {

  def apply(storedFields: Seq[String]): IndexSettings = new IndexSettings(storedFields)

  def load(dump: String): IndexSettings = {
    val json = ujson.read(dump)
    val storedFields = json("storedFields").arr.map(_.str)
    new IndexSettings(storedFields)
  }

  def fromDirectory(directory: Directory): IndexSettings =
    try {
      using(directory.openInput(OdinsonIndexWriter.SETTINGSINFO_FILENAME, new IOContext)) {
        stream =>
          IndexSettings.load(stream.readString())
      }
    } catch {
      case e: IOException => IndexSettings(Seq())
    }

}
