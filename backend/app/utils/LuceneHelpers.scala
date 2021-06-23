package utils

object LuceneHelpers {

  type Iteratorable[T] = { def iterator(): T }

  type Nextable[T] = { def next(): T }

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

  implicit class IterableFromNextable[T](nextable: Nextable[T]) extends Iterable[T] {
    override def iterator: Iterator[T] = new IteratorFromNextable(nextable)
  }

  implicit class IterableFromIteratorable[T](iteratorable: Iteratorable[Nextable[T]])
      extends Iterable[T] {
    override def iterator: Iterator[T] = iteratorable.iterator().iterator
  }

}
