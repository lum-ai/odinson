package ai.lum.odinson

import ai.lum.common.Interval

trait OdinsonMatch {

  def docID: Int
  def start: Int
  def end: Int
  def captures: List[(String, OdinsonMatch)]

  /** The interval of token indicess that form this mention. */
  def tokenInterval: Interval = Interval.open(start, end)

  /** A map from argument name to a sequence of matches.
    *
    * The value of the map is a sequence because there are events
    * that can have several arguments with the same name.
    * For example, in the biodomain, Binding may have several themes.
    */
  def arguments: Map[String, Seq[OdinsonMatch]] = {
    captures.groupBy(_._1).transform((k,v) => v.map(_._2))
  }

}

class NGramMatch(
  val docID: Int,
  val start: Int,
  val end: Int,
) extends OdinsonMatch {
  val captures: List[(String, OdinsonMatch)] = Nil
}

// TODO add traversed path to this match object
class GraphTraversalMatch(
  val srcMatch: OdinsonMatch,
  val dstMatch: OdinsonMatch,
) extends OdinsonMatch {
  val docID: Int = dstMatch.docID
  val start: Int = dstMatch.start
  val end: Int = dstMatch.end
  def captures: List[(String, OdinsonMatch)] = {
    srcMatch.captures ++ dstMatch.captures
  }
}

class ConcatMatch(
  val subMatches: List[OdinsonMatch]
) extends OdinsonMatch {

  val docID: Int = subMatches.head.docID
  val start: Int = subMatches.head.start
  val end: Int = subMatches.last.end

  def captures: List[(String, OdinsonMatch)] = {
    subMatches.flatMap(_.captures)
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

  def captures: List[(String, OdinsonMatch)] = {
    // subMatches.flatMap(_.captures)
    subMatches
      .map(_.captures)
      .foldRight(List.empty[(String, OdinsonMatch)])(_ ++ _)
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
  val captures: List[(String, OdinsonMatch)] = subMatch.captures
}

class OrMatch(
  val subMatch: OdinsonMatch,
  val clauseID: Int,
) extends OdinsonMatch {

  val docID: Int = subMatch.docID
  val start: Int = subMatch.start
  val end: Int = subMatch.end

  def captures: List[(String, OdinsonMatch)] = {
    subMatch.captures
  }

}

class NamedMatch(
  val subMatch: OdinsonMatch,
  val name: String,
) extends OdinsonMatch {

  val docID: Int = subMatch.docID
  val start: Int = subMatch.start
  val end: Int = subMatch.end

  def captures: List[(String, OdinsonMatch)] = {
    (name, subMatch) :: subMatch.captures
  }

}
