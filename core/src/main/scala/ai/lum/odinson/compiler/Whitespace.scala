package ai.lum.odinson.compiler

import fastparse.all._
import fastparse.WhitespaceApi

/** handle whitespace and comments */
object Whitespace extends WhitespaceApi.Wrapper({
  NoTrace((CharsWhile(_.isWhitespace) | Literals.comment).rep)
})
