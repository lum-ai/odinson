package ai.lum.odinson.extra

import org.apache.commons.io.FileUtils
import java.io._
import java.util.zip._
import java.nio.charset.StandardCharsets

object GzipUtils {

  def compress(data: String): Array[Byte] = {
    val baos = new ByteArrayOutputStream(data.length)
    val gzip = new GZIPOutputStream(baos)
    val bytes = data.getBytes(StandardCharsets.UTF_8)
    gzip.write(bytes)
    gzip.close()
    val compressed = baos.toByteArray
    baos.close()
    compressed
  }

  def uncompress(file: File): String = {
    val inputStream = FileUtils.openInputStream(file)
    val res = uncompress(inputStream)
    inputStream.close()
    res
  }

  def uncompress(compressed: Array[Byte]): String = {
    uncompress(new ByteArrayInputStream(compressed))
  }

  def uncompress(input: InputStream): String = {
    val gzip = new GZIPInputStream(input)
    val br = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))
    val sb = new StringBuilder()
    var line: String = br.readLine()
    while (line != null) {
      sb.append(line)
      line = br.readLine()
    }
    br.close()
    gzip.close()
    sb.toString()
  }

}
