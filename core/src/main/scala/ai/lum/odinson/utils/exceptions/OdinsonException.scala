package ai.lum.odinson.utils.exceptions

/** Custom Runtime Exception for use within Odinson.  Allow for passing in error message or wrapping
  * a caught exception. (ref: https://stackoverflow.com/a/10925403)
  * @param message
  * @param cause
  */
class OdinsonException(message: String, cause: Throwable) extends RuntimeException(message) {
  if (cause != null) { initCause(cause) }
  def this(message: String) = this(message, null)
}

object OdinsonException {

  def apply(message: String, cause: Throwable): OdinsonException = {
    new OdinsonException(message, cause)
  }

}
