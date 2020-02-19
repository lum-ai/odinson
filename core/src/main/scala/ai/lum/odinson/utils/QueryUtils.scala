package ai.lum.odinson.utils

import ai.lum.common.StringUtils._

object QueryUtils {

  /** Odinson strings don't have to be quoted if they start with a letter or underscore
   *  and are followed by zero or more letters, digits, or underscores.
   *  This function quotes the string (and escapes the necessary characters) only if needed.
   */
  def maybeQuoteWord(s: String): String = {
    val needsQuotes = "^[a-zA-Z_][a-zA-Z0-9_]*$".r.findFirstIn(s).isEmpty
    if (needsQuotes) s""""${s.escapeJava}"""" else s
  }

  /** Under certain circumstances, notably when dealing with dependency labels, Odinson
   *  accepts two extra characters in an unquoted string: the dash '-' and the colon ':'.
   *  This function quotes the string (and escapes the necessary characters) only if needed.
   */
  def maybeQuoteLabel(s: String): String = {
    val needsQuotes = "^[a-zA-Z_][a-zA-Z0-9_:-]*$".r.findFirstIn(s).isEmpty
    if (needsQuotes) s""""${s.escapeJava}"""" else s
  }

}
