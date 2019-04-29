package ai.lum.odinson

import ai.lum.common.Interval

case class OdinsonMatch(
  docID: Int,
  start: Int,
  end: Int,
  captures: List[(String, OdinsonMatch)] = Nil
) {

  /** The interval of token indicess that form this mention. */
  val tokenInterval: Interval = Interval.open(start, end)

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
