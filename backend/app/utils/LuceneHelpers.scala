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
    // This convoluted procedure is needed so that the call to hasNext(), which must
    // call nextable.next(), can store the next value which will then be retrieved with
    // the subsequent call to next() without that calling nextable.next() itself.  An
    // implicit "cursor" is in play that can provide access to other values beside the
    // current one.  The call to next() should not move the cursor away from what has
    // just been retrieved.  The cursor has already been updated in hasNext().  For
    // example, next() on TermsEnum can retrieve a BytesRef and then totalTermFreq() can
    // still access the corresponding frequency from the TermsEnum outside this interface.
    protected var hasNextOpt: Option[Boolean] = None
    protected var nextOpt: Option[T] = None

    override def hasNext: Boolean = {
      assert(hasNextOpt.isEmpty)
      nextOpt = Option(nextable.next())
      hasNextOpt = Some(nextOpt.nonEmpty)
      hasNextOpt.get
    }

    override def next(): T = {
      assert(hasNextOpt.contains(true))
      val result = nextOpt.get
      assert(result != null)
      hasNextOpt = None
      result
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
