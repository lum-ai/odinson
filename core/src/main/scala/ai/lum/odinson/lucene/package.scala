package ai.lum.odinson

package object lucene {

  type NamedCapture = (String, Span)
  type NamedCaptures = Map[String, Seq[Span]]

  object QuantifierType extends Enumeration {
    type QuantifierType = Value
    val Greedy, Lazy = Value
  }

}
