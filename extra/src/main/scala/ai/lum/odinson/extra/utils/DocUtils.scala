package ai.lum.odinson.extra

import java.nio.charset.StandardCharsets.UTF_8

import org.clulab.processors.Sentence
import org.clulab.serialization.json._

/** Utilities for encoding/decoding [[org.clulab.serialization.json.SentenceOps]] to/from bytes */
object DocUtils {

  def sentenceToBytes(s: Sentence): Array[Byte] = {
    s.json(pretty = false).getBytes(UTF_8)
  }

  def bytesToJsonString(bytes: Array[Byte]): String = {
    new String(bytes, UTF_8)
  }

}
