package ai.lum.odinson.lucene

import ai.lum.common.Interval

case class Span(start: Int, end: Int) {
  def interval: Interval = Interval.open(start, end)
}

object Span {

  implicit def orderingByStartEnd[A <: Span]: Ordering[A] = {
    Ordering.by(span => (span.start, span.end))
  }

  def orderingByEndStart[A <: Span]: Ordering[A] = {
    Ordering.by(span => (span.end, span.start))
  }

}

case class SpanWithCaptures(span: Span, captures: List[NamedCapture])
