package ai.lum.odinson.extra.utils

import java.io.File
import ai.lum.common.FileUtils._

object FileUtils {

  def resolveFile(f: File, resolveToDir: File): File = {
    val parentPath = f.getParentFile.toPath
    val relativeFile = parentPath.relativize(f.toPath)
    val resolvedPath = resolveToDir.toPath.resolve(relativeFile)
    val resolvedFile = inputFileInDocsDir.getParent
      .resolve(inputFileInDocsDir.getFileName.toFile.getBaseName() + ".json.gz").toFile
    docFile.toPath.getParent.toFile.mkdirs
  }


}
