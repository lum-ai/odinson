package ai.lum.odinson.lucene

import org.apache.lucene.util.PriorityQueue

class SpanPositionQueue(maxSize: Int) extends PriorityQueue[OdinSpans](maxSize, false) {
  protected def lessThan(s1: OdinSpans, s2: OdinSpans): Boolean = {
    val start1 = s1.startPosition()
    val start2 = s2.startPosition()
    if (start1 < start2) true
    else if (start1 == start2) s1.endPosition() < s2.endPosition()
    else false
  }
}

class QueueByPosition(maxSize: Int) extends PriorityQueue[SpanWithCaptures](maxSize, false) {
  protected def lessThan(lhs: SpanWithCaptures, rhs: SpanWithCaptures): Boolean = {
    if (lhs.span.start < rhs.span.start) true
    else if (lhs.span.start == rhs.span.start) lhs.span.end < rhs.span.end
    else false
  }
}

object QueueByPosition {
  def mkPositionQueue(spans: Seq[SpanWithCaptures]): QueueByPosition = {
    val queue = new QueueByPosition(spans.length)
    for (s <- spans) {
      queue.add(s)
    }
    queue
  }
}
