package utils

import scala.language.reflectiveCalls

// TODO: Can this be moved to core?
/** Tools to help convey Lucene's data structures into Scala data structures.
  */
object LuceneHelpers {

  /** A trait for any structure that has a parameterless `iterator` method, e.g. Lucene's
    * [[org.apache.lucene.index.Terms]].
    * @tparam T the type of the iterator.
    */
  type Iteratorable[T] = { def iterator(): T }

  /** A trait for any structure that has a parameterless `next` method, e.g. Lucene's
    * [[org.apache.lucene.index.TermsEnum]].
    * @tparam T the type of the emitted data.
    */
  type Nextable[T] = { def next(): T }

  /** An [[Iterator]] that derives its next element from a [[Nextable]].
    * @param nextable the Nextable from which the Iterator will get a next element.
    * @tparam T the element type of the collection.
    */
  class IteratorFromNextable[T](nextable: Nextable[T]) extends Iterator[T] {
    protected var nextOpt: Option[T] = Option(nextable.next())

    override def hasNext: Boolean = nextOpt.isDefined

    override def next(): T = {
      nextOpt.map { next =>
        nextOpt = Option(nextable.next())
        next
      }
        .getOrElse {
          throw new RuntimeException("No more elements!")
        }
    }

  }

  /** An [[Iterable]] whose iterator is derived from a structure with the [[Nextable]] trait.
    * @param nextable the Nextable from which iterator is derived.
    * @tparam T the element type of the collection.
    */
  implicit class IterableFromNextable[T](nextable: Nextable[T]) extends Iterable[T] {
    override def iterator: Iterator[T] = new IteratorFromNextable(nextable)
  }

  /** A Scala [[Iterable]] that is generated from an [[Iteratorable]].
    * @param iteratorable the Iteratorable that generates the iterator.
    * @tparam T the element type of the collection.
    */
  implicit class IterableFromIteratorable[T](iteratorable: Iteratorable[Nextable[T]])
      extends Iterable[T] {
    override def iterator: Iterator[T] = iteratorable.iterator().iterator
  }

}
