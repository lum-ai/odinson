package ai.lum.odinson.digraph

import scala.util.matching.Regex
import ai.lum.common.StringUtils._

sealed trait LabelMatcher {
  def matches(labelId: Int): Boolean
}

/** A LabelMatcher that always fails */
object FailLabelMatcher extends LabelMatcher {
  override def toString: String = "FailLabelMatcher"
  def matches(labelId: Int): Boolean = false
}

/** Matches a string exactly. Actually compares word ids for efficiency. */
// FIXME should we remove string?
class ExactLabelMatcher(val string: String, val id: Int) extends LabelMatcher {
  override def toString: String = "\"" + string.escapeJava + "\""
  def matches(labelId: Int): Boolean = labelId == id
}

/** Matches a string using a regex.
  *  Anchors regex so that it behaves closer to lucene regex.
  */
class RegexLabelMatcher(val regex: Regex, val vocabulary: Vocabulary)
    extends LabelMatcher {

  override def toString: String =
    s"/${regex.pattern.pattern.replaceAllLiterally("/", "\\/")}/"

  // mimic lucene regex behavior (i.e., regex must match the whole string)
  def matches(labelId: Int): Boolean = vocabulary.getTerm(labelId) match {
    case Some(regex.anchored(_*)) => true
    case _                        => false
  }

}
