package ai.lum.odinson.compiler

import fastparse._
import NoWhitespace._
import ai.lum.common.StringUtils._

object Literals {

  /** matches either an identifier or a quoted string */
  def string[_: P]: P[String] = P(identifier | quotedString)

  /** matches either an identifier or a quoted string */
  def extendedString[_: P]: P[String] = P(extendedIdentifier | quotedString)

  /** matches a valid java identifier */
  def identifier[_: P]: P[String] = {
    P(
      CharPred(c => c.isUnicodeIdentifierStart || c == '_') ~
      CharsWhile(_.isUnicodeIdentifierPart).?
    ).!
  }

  /** matches a string that may contain colons and/or dashes */
  def extendedIdentifier[_: P]: P[String] = {
    P(
      CharPred(c => c.isUnicodeIdentifierStart || c == '_') ~
      CharsWhile(c => c.isUnicodeIdentifierPart || c == ':' || c == '-').?
    ).!
  }

  /** matches an unsigned integer */
  def unsignedInt[_: P]: P[Int] = {
    P(CharsWhile(_.isDigit)).!.map(_.toInt)
  }

  /** matches a single- or double-quoted string */
  def quotedString[_: P]: P[String] = {
    P(delimitedString('\'', '\\') | delimitedString('"', '\\')).map {
      // drop quotes and unescape using java rules
      s => s.drop(1).dropRight(1).unescapeJava
    }
  }

  /** matches a slash-delimited string */
  def regex[_: P]: P[String] = P(delimitedString('/', '\\')).map {
    // drop delimiting forward slashes and unescape forward slashes only,
    // let the regex engine handle the rest
    s => s.drop(1).dropRight(1).replaceAll("""\\/""", "/")
  }

  /**
   * The parser constructed by this method returns the delimited string as it
   * was found. It is the caller's job to drop delimiters and unescape any
   * escaped char.
   */
  def delimitedString[_: P](delimiter: Char, escape: Char): P[String] = {
    P(
      // open delimiter
      CharPred(_ == delimiter) ~
      // valid characters
      CharsWhile(c => c != delimiter && c != escape).? ~
      (
        // an escaped character
        (if (delimiter == escape) CharPred(_ == delimiter).rep(exactly = 2) else CharPred(_ == escape) ~ AnyChar) ~
        // valid characters
        CharsWhile(c => c != delimiter && c != escape).?
      ).rep ~
      // close delimiter
      CharPred(_ == delimiter)
    ).!
  }

}
