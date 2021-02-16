package ai.lum.odinson.utils

import java.io.{ ByteArrayInputStream, File, InputStream }
import java.nio.charset.StandardCharsets

import ai.lum.common.FileUtils._
import ai.lum.odinson.utils.RuleSources.RuleSources
import ai.lum.odinson.utils.exceptions.OdinsonException

case class SituatedStream(stream: InputStream, canonicalPath: String, from: RuleSources) {

  def relativePathStream(path: String): SituatedStream = {
    val newPath = resolveRelativePath(path)
    from match {
      case RuleSources.resource => SituatedStream.fromResource(newPath)
      case RuleSources.file     => SituatedStream.fromFile(newPath)
      case _                    => ???
    }
  }

  def resolveRelativePath(path: String): String = {
    if (isAbsolute(path)) {
      return path
    }
    // handle relative
    from match {
      case RuleSources.resource =>
        var prevPath = canonicalPath
        if (!prevPath.endsWith("/")) {
          val lastSep = prevPath.lastIndexOf("/")
          prevPath = prevPath.slice(0, lastSep)
          prevPath += "/"
        }
        prevPath + path
      case RuleSources.file =>
        val parent = new File(canonicalPath).getParentFile.getCanonicalPath
        new File(parent, path).getCanonicalPath
      case RuleSources.string =>
        throw new OdinsonException("Strings don't support imports and relative paths")
    }
  }

  def isAbsolute(path: String): Boolean = from match {
    case RuleSources.file     => new File(path).isAbsolute
    case RuleSources.resource => path.startsWith("/")
    case _                    => ???
  }

}

object SituatedStream {
  val NO_PATH = "NO_PATH"

  def fromFile(canonicalPath: String): SituatedStream = {
    new SituatedStream(new File(canonicalPath).toInputStream, canonicalPath, RuleSources.file)
  }

  def fromResource(resourcePath: String): SituatedStream = {
    val stream = getClass.getResourceAsStream(resourcePath)
    new SituatedStream(stream, resourcePath, RuleSources.resource)
  }

  def fromString(s: String): SituatedStream = {
    val stream = new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8))
    new SituatedStream(stream, SituatedStream.NO_PATH, RuleSources.string)
  }

}

object RuleSources extends Enumeration {
  type RuleSources = Value
  val resource = Value("resource")
  val file = Value("file")
  val string = Value("string")
}
