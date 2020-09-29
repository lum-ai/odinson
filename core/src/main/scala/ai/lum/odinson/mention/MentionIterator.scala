package ai.lum.odinson.mention

trait MentionIterator extends Iterator[Mention] {
  def close(): Unit
}

class MentionsIterator(mentionIterators: Seq[MentionIterator]) extends MentionIterator {
  protected val iterator = mentionIterators.foldLeft(Iterator.empty.asInstanceOf[Iterator[Mention]])(_ ++ _)

  override def close(): Unit = mentionIterators.foreach(_.close)

  override def hasNext: Boolean = iterator.hasNext

  override def next(): Mention = iterator.next
}

object MentionIterator {

  def concatenate(iterators: Seq[MentionIterator]): MentionIterator = new MentionsIterator(iterators)
}