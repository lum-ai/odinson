package ai.lum.odinson.utils

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.AbstractIterator

class DistinctBy[A, B](
  val underlying: Iterator[A],
  val f: A => B,
) extends AbstractIterator[A] {

  def this(underlying: Seq[A], f: A => B) = {
    this(underlying.iterator, f)
  }

  private[this] val traversedValues = mutable.HashSet.empty[B]
  private[this] var nextElementDefined: Boolean = false
  private[this] var nextElement: A = _

  @tailrec
  final def hasNext: Boolean = {
    if (nextElementDefined) return true
    if (!underlying.hasNext) return false
    val a = underlying.next()
    if (traversedValues.add(f(a))) {
      nextElement = a
      nextElementDefined = true
      true
    } else {
      hasNext
    }
  }

  def next(): A = {
    if (hasNext) {
      nextElementDefined = false
      nextElement
    } else {
      Iterator.empty.next()
    }
  }

}
