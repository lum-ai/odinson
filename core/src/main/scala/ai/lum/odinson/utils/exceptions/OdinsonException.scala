package ai.lum.odinson.utils.exceptions

/** Custom Runtime Exception for use within Odinson.  Allow for passing in error message or wrapping
  * a caught exception. (ref: https://stackoverflow.com/a/10925403)
  * @param message
  * @param cause
  */
class OdinsonException(message: String = null, cause: Throwable = null)
    extends RuntimeException(OdinsonException.defaultMessage(message, cause), cause) {}

object OdinsonException {

  val METADATA_STRING_FIELD_EXCEPTION: OdinsonException = new OdinsonException(
    "StringField metadata is not supported.  Supported types: TokensField, NumberField, DateField"
  )

  def defaultMessage(message: String, cause: Throwable) =
    if (message != null) message
    else if (cause != null) cause.toString()
    else null

}
