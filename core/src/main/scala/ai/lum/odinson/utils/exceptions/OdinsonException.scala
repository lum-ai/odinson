package ai.lum.odinson.utils.exceptions

/** Custom Runtime Exception for use within Odinson.  Allow for passing in error message or wrapping
  * a caught exception. (ref: https://stackoverflow.com/a/10925403)
  * @param message
  * @param cause
  */
class OdinsonException(message: String, cause: Option[Throwable])
    extends RuntimeException(message) {
  cause.map(initCause)
}

object OdinsonException {

  def apply(message: String) = new OdinsonException(message, None)
  def apply(message: String, cause: Throwable) = new OdinsonException(message, Some(cause))

}
