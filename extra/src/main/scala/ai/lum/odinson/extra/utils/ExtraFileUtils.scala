package ai.lum.odinson.extra.utils

import java.io.File
import ai.lum.common.FileUtils._

object ExtraFileUtils {

  def resolveFileWithNewExtension(
    f: File,
    relativizeAgainst: File,
    resolveTo: File,
    ext: String
  ): File = {
    val resolved = resolveFile(f, relativizeAgainst, resolveTo)
    // where it belongs
    val parent = resolved.getParentFile
    // make a new name from the basename and the new extension
    val baseName = resolved.getBaseName()
    new File(parent, s"$baseName$ext")
  }

  def resolveFile(f: File, relativizeFromDir: File, resolveToDir: File): File = {
    // Check to see if resolution is unnecessary
    if (relativizeFromDir == resolveToDir) return f
    // make f into a relative path, where all the directory structure between
    // the relativizeFromDir and f is kept
    val relativeFile = relativizeFromDir.toPath.relativize(f.toPath)
    // resolve that relative path to the provided File to impose
    // the parallel subdirectory structure
    val resolvedFile = resolveToDir.toPath.resolve(relativeFile).toFile
    // make the subdirectories if they are not already made
    resolvedFile.getParentFile.mkdirs

    resolvedFile
  }

}
