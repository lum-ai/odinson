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

  /** Constructs a quantifier for the provided requirements. */
  def quantifier(max: Int): String = {
    quantifier(0, Some(max), reluctant = false)
  }

  /** Constructs a quantifier for the provided requirements. */
  def quantifier(max: Int, reluctant: Boolean): String = {
    quantifier(0, Some(max), reluctant)
  }

  /** Constructs a quantifier for the provided requirements. */
  def quantifier(min: Int, max: Int): String = {
    quantifier(min, Some(max), reluctant = false)
  }

  /** Constructs a quantifier for the provided requirements. */
  def quantifier(min: Int, max: Int, reluctant: Boolean): String = {
    quantifier(min, Some(max), reluctant)
  }

  /** Constructs a quantifier for the provided requirements. */
  def quantifier(min: Int, max: Option[Int]): String = {
    quantifier(min, max, reluctant = false)
  }

  /** Constructs a quantifier for the provided requirements. */
  def quantifier(min: Int, max: Option[Int], reluctant: Boolean): String = {
    require(max.isEmpty || min < max.get, s"min=$min must be less than max=${max.get}")
    val q = (min, max) match {
      case (0,   Some(1))   => "?"
      case (0,   None)      => "*"
      case (1,   None)      => "+"
      case (min, None)      => s"{$min,}"
      case (0,   Some(max)) => s"{,$max}"
      case (min, Some(max)) => s"{$min,$max}"
    }
    if (reluctant) s"$q?" else q
  }

}
