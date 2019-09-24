package ai.lum.odinson

import scala.util.hashing.MurmurHash3._
import ai.lum.common.Interval

case class NamedCapture(name: String, capturedMatch: OdinsonMatch)

sealed trait OdinsonMatch {

  def docID: Int
  def start: Int
  def end: Int
  def namedCaptures: List[NamedCapture]

  /** The length of the match */
  def length: Int = end - start

  /** The interval of token indices that form this mention. */
  def tokenInterval: Interval = Interval.open(start, end)

  /** A map from argument name to a sequence of matches.
    *
    * The value of the map is a sequence because there are events
    * that can have several arguments with the same name.
    * For example, in the biodomain, Binding may have several themes.
    */
  def arguments: Map[String, Seq[OdinsonMatch]] = {
    namedCaptures
      .groupBy(_.name)
      .transform((k,v) => v.map(_.capturedMatch))
  }

  def canEqual(other: Any): Boolean = {
    other.isInstanceOf[OdinsonMatch]
  }

  override def equals(other: Any): Boolean = {
    other match {
      case that: OdinsonMatch =>
        (that canEqual this) &&
        this.docID == that.docID &&
        this.start == that.start &&
        this.end == that.end &&
        this.namedCaptures == that.namedCaptures
      case _ => false
    }
  }

}

class EventMatch(
  val trigger: OdinsonMatch,
  val namedCaptures: List[NamedCapture],
) extends OdinsonMatch {
  val docID: Int = trigger.docID
  val start: Int = (trigger.start :: namedCaptures.map(_.capturedMatch.start)).min
  val end: Int = (trigger.end :: namedCaptures.map(_.capturedMatch.end)).max
  override val hashCode: Int = {
    (docID, start, end, unorderedHash(namedCaptures)).##
  }
}

class NGramMatch(
  val docID: Int,
  val start: Int,
  val end: Int,
) extends OdinsonMatch {
  val namedCaptures: List[NamedCapture] = Nil
  override val hashCode: Int = {
    (docID, start, end).##
  }
}

// TODO add traversed path to this match object
class GraphTraversalMatch(
  val srcMatch: OdinsonMatch,
  val dstMatch: OdinsonMatch,
) extends OdinsonMatch {
  val docID: Int = dstMatch.docID
  val start: Int = dstMatch.start
  val end: Int = dstMatch.end
  val namedCaptures: List[NamedCapture] = {
    srcMatch.namedCaptures ++ dstMatch.namedCaptures
  }
  override val hashCode: Int = {
    (docID, start, end, unorderedHash(namedCaptures)).##
  }
}

class ConcatMatch(
  val subMatches: List[OdinsonMatch]
) extends OdinsonMatch {
  val docID: Int = subMatches.head.docID
  val start: Int = subMatches.head.start
  val end: Int = subMatches.last.end
  val namedCaptures: List[NamedCapture] = {
    subMatches.flatMap(_.namedCaptures)
  }
  override val hashCode: Int = {
    (docID, start, end, unorderedHash(namedCaptures)).##
  }
}

class RepetitionMatch(
  val subMatches: List[OdinsonMatch],
  val isGreedy: Boolean,
) extends OdinsonMatch {
  val docID: Int = subMatches.head.docID
  val start: Int = subMatches.head.start
  val end: Int = subMatches.last.end
  val isLazy: Boolean = !isGreedy
  val namedCaptures: List[NamedCapture] = {
    subMatches.flatMap(_.namedCaptures)
  }
  override val hashCode: Int = {
    (docID, start, end, unorderedHash(namedCaptures)).##
  }
}

class OptionalMatch(
  val subMatch: OdinsonMatch,
  val isGreedy: Boolean,
) extends OdinsonMatch {
  val docID: Int = subMatch.docID
  val start: Int = subMatch.start
  val end: Int = subMatch.end
  val isLazy: Boolean = !isGreedy
  def namedCaptures: List[NamedCapture] = {
    subMatch.namedCaptures
  }
  override val hashCode: Int = {
    (docID, start, end, unorderedHash(namedCaptures)).##
  }
}

class OrMatch(
  val subMatch: OdinsonMatch,
  val clauseID: Int,
) extends OdinsonMatch {
  val docID: Int = subMatch.docID
  val start: Int = subMatch.start
  val end: Int = subMatch.end
  def namedCaptures: List[NamedCapture] = {
    subMatch.namedCaptures
  }
  override val hashCode: Int = {
    (docID, start, end, unorderedHash(namedCaptures)).##
  }
}

class NamedMatch(
  val subMatch: OdinsonMatch,
  val name: String,
) extends OdinsonMatch {
  val docID: Int = subMatch.docID
  val start: Int = subMatch.start
  val end: Int = subMatch.end
  def namedCaptures: List[NamedCapture] = {
    NamedCapture(name, subMatch) :: subMatch.namedCaptures
  }
  override val hashCode: Int = {
    (docID, start, end, unorderedHash(namedCaptures)).##
  }
}
