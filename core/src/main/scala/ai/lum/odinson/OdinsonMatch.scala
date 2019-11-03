package ai.lum.odinson

import scala.util.hashing.MurmurHash3._
import ai.lum.common.Interval

case class NamedCapture(name: String, capturedMatch: OdinsonMatch)

sealed trait OdinsonMatch {

  def docID: Int
  def start: Int
  def end: Int
  def namedCaptures: Array[NamedCapture]

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
  def arguments: Map[String, Array[OdinsonMatch]] = {
    namedCaptures
      .groupBy(_.name)
      .transform((k,v) => v.map(_.capturedMatch))
  }

}

object OdinsonMatch {
  val emptyNamedCaptures = new Array[NamedCapture](0)
}

class EventMatch(
  val trigger: OdinsonMatch,
  val namedCaptures: Array[NamedCapture],
) extends OdinsonMatch {

  val docID: Int = trigger.docID

  val start: Int = {
    var minStart = trigger.start
    var i = 0
    while (i < namedCaptures.length) {
      val cStart = namedCaptures(i).capturedMatch.start
      if (cStart < minStart) minStart = cStart
      i += 1
    }
    minStart
  }

  val end: Int = {
    var maxEnd = trigger.end
    var i = 0
    while (i < namedCaptures.length) {
      val cEnd = namedCaptures(i).capturedMatch.end
      if (cEnd > maxEnd) maxEnd = cEnd
      i += 1
    }
    maxEnd
  }

}

class NGramMatch(
  val docID: Int,
  val start: Int,
  val end: Int,
) extends OdinsonMatch {
  val namedCaptures: Array[NamedCapture] = OdinsonMatch.emptyNamedCaptures
}

// TODO add traversed path to this match object
class GraphTraversalMatch(
  val srcMatch: OdinsonMatch,
  val dstMatch: OdinsonMatch,
) extends OdinsonMatch {

  val docID: Int = dstMatch.docID
  val start: Int = dstMatch.start
  val end: Int = dstMatch.end

  def namedCaptures: Array[NamedCapture] = {
    val srcCaps = srcMatch.namedCaptures
    val dstCaps = dstMatch.namedCaptures
    val length = srcCaps.length + dstCaps.length
    val totalCaps = new Array[NamedCapture](length)
    System.arraycopy(srcCaps, 0, totalCaps, 0, srcCaps.length)
    System.arraycopy(dstCaps, 0, totalCaps, srcCaps.length, dstCaps.length)
    totalCaps
  }

}

class ConcatMatch(
  val subMatches: Array[OdinsonMatch]
) extends OdinsonMatch {
  val docID: Int = subMatches(0).docID
  val start: Int = subMatches(0).start
  val end: Int = subMatches(subMatches.length - 1).end
  def namedCaptures: Array[NamedCapture] = {
    subMatches.flatMap(_.namedCaptures)
  }
}

class RepetitionMatch(
  val subMatches: Array[OdinsonMatch],
  val isGreedy: Boolean,
) extends OdinsonMatch {
  val docID: Int = subMatches(0).docID
  val start: Int = subMatches(0).start
  val end: Int = subMatches(subMatches.length - 1).end
  val isLazy: Boolean = !isGreedy
  def namedCaptures: Array[NamedCapture] = {
    subMatches.flatMap(_.namedCaptures)
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
  def namedCaptures: Array[NamedCapture] = {
    subMatch.namedCaptures
  }
}

class OrMatch(
  val subMatch: OdinsonMatch,
  val clauseID: Int,
) extends OdinsonMatch {
  val docID: Int = subMatch.docID
  val start: Int = subMatch.start
  val end: Int = subMatch.end
  def namedCaptures: Array[NamedCapture] = {
    subMatch.namedCaptures
  }
}

class NamedMatch(
  val subMatch: OdinsonMatch,
  val name: String,
) extends OdinsonMatch {

  val docID: Int = subMatch.docID
  val start: Int = subMatch.start
  val end: Int = subMatch.end

  def namedCaptures: Array[NamedCapture] = {
    val subCaps = subMatch.namedCaptures
    val newCaps = new Array[NamedCapture](subCaps.length + 1)
    newCaps(0) = NamedCapture(name, subMatch)
    System.arraycopy(subCaps, 0, newCaps, 1, subCaps.length)
    newCaps
  }

}
