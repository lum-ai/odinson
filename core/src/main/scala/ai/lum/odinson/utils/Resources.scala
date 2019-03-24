package ai.lum.odinson.utils

import scala.util.control.NonFatal
import scala.language.reflectiveCalls

// TODO should this be in ai.lum.common?
object Resources {

  type Closeable = { def close(): Unit }

  /** poor man's try-with-resources */
  def using[A <: Closeable, B](resource: A)(f: A => B): B = {
    require(resource != null, "resource is null")
    try {
      f(resource)
    } catch {
      case e1: Throwable =>
        try {
          resource.close()
        } catch {
          case NonFatal(e2) =>
            e1.addSuppressed(e2)
          case e2: Throwable =>
            e2.addSuppressed(e1)
            throw e2
        }
        throw e1
    } finally {
      resource.close()
    }
  }

}
