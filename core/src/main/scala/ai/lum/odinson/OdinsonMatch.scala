package ai.lum.odinson

import ai.lum.common.Interval
import ai.lum.odinson.lucene.search.ArgumentSpans

case class NamedCapture(name: String, label: Option[String], capturedMatch: OdinsonMatch)

sealed trait OdinsonMatch {

  def start: Int
  def end: Int
  def namedCaptures: Array[NamedCapture]

  /** The length of the match */
  def length: Int = end - start

  /** The interval of token indices that form this mention. */
  def tokenInterval: Interval = Interval.open(start, end)

}

object OdinsonMatch {
  val emptyNamedCaptures: Array[NamedCapture] = Array.empty
}

case class StateMatch(start: Int, end: Int, namedCaptures: Array[NamedCapture])
    extends OdinsonMatch {

  override def toString: String = {
    val namedCapturesString = namedCaptures.map { namedCapture =>
      s"NamedCapture(${namedCapture.name},${namedCapture.label},${namedCapture.capturedMatch}"
    }.mkString("NamedCaptures(", ",", ")")

    (s"StateMatch($start,$end,$namedCapturesString")
  }

}

object StateMatch {

  protected def recFromOdinsonMatch(odinsonMatch: OdinsonMatch): StateMatch = {
    StateMatch(
      odinsonMatch.start,
      odinsonMatch.end,
      odinsonMatch.namedCaptures.map { namedCapture =>
        namedCapture.copy(capturedMatch = recFromOdinsonMatch(namedCapture.capturedMatch))
      }
    )
  }

  def fromOdinsonMatch(odinsonMatch: OdinsonMatch): StateMatch = recFromOdinsonMatch(odinsonMatch)
}

/** helper class to store the metadata related to an EventMention's argument,
  *  like it's name and some information about its quantifiers.
  */
case class ArgumentMetadata(name: String, min: Int, max: Option[Int], promote: Boolean)

class EventMatch(
  val trigger: OdinsonMatch,
  val namedCaptures: Array[NamedCapture],
  val argumentMetadata: Array[ArgumentMetadata]
) extends OdinsonMatch {

  val start: Int = trigger.start
  val end: Int = trigger.end

  /** Removes all arguments that overlap with the trigger and returns Some(EventMention)
    *  if the surviving arguments still satisfy the event specification (e.g. all required arguments survive),
    *  or None if the surviving arguments are not sufficient to construct a valid EventMention.
    */
  def removeTriggerOverlaps: Option[EventMatch] = {
    val captures =
      namedCaptures.filterNot(_.capturedMatch.tokenInterval intersects trigger.tokenInterval)
    val groupedCaptures = captures.groupBy(_.name)
    for (meta <- argumentMetadata) {
      val numMatches = groupedCaptures.get(meta.name).map(_.length).getOrElse(0)
      if (numMatches < meta.min) {
        return None
      }
    }
    Some(new EventMatch(trigger, captures, argumentMetadata))
  }

}

/** This class represents a partial event match
  *  that has to be packaged into actual event mentions.
  *  This class is for internal purposes only and should
  *  never be seen by an end user.
  */
class EventSketch(
  val trigger: OdinsonMatch,
  val argSketches: Array[(ArgumentSpans, OdinsonMatch)]
) extends OdinsonMatch {
  val start: Int = trigger.start
  val end: Int = trigger.end
  val namedCaptures: Array[NamedCapture] = OdinsonMatch.emptyNamedCaptures

  val argumentMetadata: Array[ArgumentMetadata] = {
    val metadata = argSketches.map { a =>
      // If we need to promote
      val promote = a._1.promote
      ArgumentMetadata(a._1.name, a._1.min, a._1.max, promote)
    }
    metadata.distinct
  }

}

class NGramMatch(
  val start: Int,
  val end: Int
) extends OdinsonMatch {
  val namedCaptures: Array[NamedCapture] = OdinsonMatch.emptyNamedCaptures
}

// TODO add traversed path to this match object
class GraphTraversalMatch(
  val srcMatch: OdinsonMatch,
  val dstMatch: OdinsonMatch
) extends OdinsonMatch {

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
  val start: Int = subMatches(0).start
  val end: Int = subMatches(subMatches.length - 1).end

  def namedCaptures: Array[NamedCapture] = {
    subMatches.flatMap(_.namedCaptures)
  }

}

class RepetitionMatch(
  val subMatches: Array[OdinsonMatch],
  val isGreedy: Boolean
) extends OdinsonMatch {
  val start: Int = subMatches(0).start
  val end: Int = subMatches(subMatches.length - 1).end
  val isLazy: Boolean = !isGreedy

  def namedCaptures: Array[NamedCapture] = {
    subMatches.flatMap(_.namedCaptures)
  }

}

class OptionalMatch(
  val subMatch: OdinsonMatch,
  val isGreedy: Boolean
) extends OdinsonMatch {
  val start: Int = subMatch.start
  val end: Int = subMatch.end
  val isLazy: Boolean = !isGreedy

  def namedCaptures: Array[NamedCapture] = {
    subMatch.namedCaptures
  }

}

class OrMatch(
  val subMatch: OdinsonMatch,
  val clauseID: Int
) extends OdinsonMatch {
  val start: Int = subMatch.start
  val end: Int = subMatch.end

  def namedCaptures: Array[NamedCapture] = {
    subMatch.namedCaptures
  }

}

class NamedMatch(
  val subMatch: OdinsonMatch,
  val name: String,
  val label: Option[String]
) extends OdinsonMatch {

  val start: Int = subMatch.start
  val end: Int = subMatch.end

  def namedCaptures: Array[NamedCapture] = {
    val subCaps = subMatch.namedCaptures
    val newCaps = new Array[NamedCapture](subCaps.length + 1)
    newCaps(0) = NamedCapture(name, label, subMatch)
    System.arraycopy(subCaps, 0, newCaps, 1, subCaps.length)
    newCaps
  }

}
