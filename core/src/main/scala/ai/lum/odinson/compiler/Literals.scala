package ai.lum.odinson.compiler

import fastparse.all._
import ai.lum.common.StringUtils._

object Literals {

  /** matches a comment and throws it away */
  val comment: P[Unit] = {
    P(CharPred(_ == '#') ~ CharsWhile(_ != '\n', min = 0))
  }

  /** matches either an identifier or a quoted string */
  val string: P[String] = P(odinIdentifier | quotedString)

  /** matches a valid java identifier */
  val javaIdentifier: P[String] = {
    P(
      CharPred(_.isUnicodeIdentifierStart) ~
      CharsWhile(_.isUnicodeIdentifierPart, min = 0)
    ).!
  }

  /** matches a string that may contain colons and/or dashes */
  val odinIdentifier: P[String] = {
    P(
      CharPred(_.isUnicodeIdentifierStart) ~
      CharsWhile(c => c.isUnicodeIdentifierPart || c == ':' || c == '-', min = 0)
    ).!
  }

  /** matches an unsigned integer */
  val unsignedInt: P[Int] = {
    P(CharsWhile(_.isDigit)).!.map(_.toInt)
  }

  /** matches a single- or double-quoted string */
  val quotedString: P[String] = {
    P(delimitedString('\'', '\\') | delimitedString('"', '\\')).map {
      // drop quotes and unescape using java rules
      s => s.drop(1).dropRight(1).unescapeJava
    }
  }

  /** matches a slash-delimited string */
  val regex: P[String] = P(delimitedString('/', '\\')).map {
    // drop delimiting forward slashes and unescape forward slashes only,
    // let the regex engine handle the rest
    s => s.drop(1).dropRight(1).replaceAll("""\\/""", "/")
  }

  /**
   * The parser constructed by this method returns the delimited string as it
   * was found. It is the caller's job to drop delimiters and unescape any
   * escaped char.
   */
  def delimitedString(delimiter: Char, escape: Char): Parser[String] = {
    val delimiterChar = CharPred(_ == delimiter)
    val validChars = CharsWhile(c => c != delimiter && c != escape, min = 0)
    val escapedChar =
      if (delimiter == escape) {
        // if the delimiter and escape characters are the same
        // then only the delimiter can be escaped
        delimiterChar ~ delimiterChar
      } else {
        // if the delimiter and escape characters are different
        // then any character can be escaped
        CharPred(_ == escape) ~ AnyChar
      }
    // return parser
    P(
      delimiterChar ~
      validChars ~
      (escapedChar ~ validChars).rep ~
      delimiterChar
    ).!
  }

}
