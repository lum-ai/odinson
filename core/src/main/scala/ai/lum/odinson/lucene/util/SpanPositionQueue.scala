package ai.lum.odinson.lucene.util

import org.apache.lucene.util.PriorityQueue
import ai.lum.odinson._
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search.spans._

class SpanPositionQueue(maxSize: Int) extends PriorityQueue[OdinsonSpans](maxSize, false) {
  protected def lessThan(s1: OdinsonSpans, s2: OdinsonSpans): Boolean = {
    val start1 = s1.startPosition()
    val start2 = s2.startPosition()
    if (start1 < start2) true
    else if (start1 == start2) s1.endPosition() < s2.endPosition()
    else false
  }
}

class QueueByPosition(maxSize: Int) extends PriorityQueue[OdinsonMatch](maxSize, false) {
  protected def lessThan(lhs: OdinsonMatch, rhs: OdinsonMatch): Boolean = {
    if (lhs.start < rhs.start) true
    else if (lhs.start == rhs.start) lhs.end < rhs.end
    else false
  }
}

object QueueByPosition {
  def mkPositionQueue(spans: Seq[OdinsonMatch]): QueueByPosition = {
    val queue = new QueueByPosition(spans.length)
    for (s <- spans) {
      queue.add(s)
    }
    queue
  }
}
