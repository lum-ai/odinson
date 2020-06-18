package ai.lum.odinson.utils

import ai.lum.common.StringUtils._

object QueryUtils {

  /** Odinson strings don't have to be quoted if they start with a letter or
   *  underscore and are followed by zero or more letters, digits, or underscores.
   *  This function quotes the string (and escapes the necessary characters)
   *  only if needed.
   */
  def maybeQuoteWord(s: String): String = {
    val needsQuotes = "^[a-zA-Z_][a-zA-Z0-9_]*$".r.findFirstIn(s).isEmpty
    if (needsQuotes) s""""${s.escapeJava}"""" else s
  }

  /** Under certain circumstances, notably when dealing with dependency labels,
   *  Odinson accepts two extra characters in an unquoted string: the dash '-'
   *  and the colon ':'. This function quotes the string (and escapes
   *  the necessary characters) only if needed.
   */
  def maybeQuoteLabel(s: String): String = {
    val needsQuotes = "^[a-zA-Z_][a-zA-Z0-9_:-]*$".r.findFirstIn(s).isEmpty
    if (needsQuotes) s""""${s.escapeJava}"""" else s
  }

  /** Constructs a quantifier for the provided requirements. */
  def quantifier(n: Int): String = {
    quantifier(n, Some(n), reluctant = false)
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
    def error(msg: String) = throw new IllegalArgumentException(msg)
    val q = (min, max) match {
      case (min, _) if min < 0 => error(s"min=$min can't be negative")
      case (0, Some(1)) => "?"
      case (0, None) => "*"
      case (1, None) => "+"
      case (min, Some(max)) if min == max => s"{$min}"
      case (min, None) => s"{$min,}"
      case (0, Some(max)) => s"{,$max}"
      case (min, Some(max)) if min > max => error(s"min=$min > max=$max")
      case (min, Some(max)) => s"{$min,$max}"
    }
    // exact repetition can't be reluctant
    if (reluctant && (max.isEmpty || min != max.get)) s"$q?" else q
  }

}
