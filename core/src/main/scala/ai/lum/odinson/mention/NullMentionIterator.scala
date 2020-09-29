package ai.lum.odinson.mention

class NullMentionIterator extends MentionIterator {

  override def close(): Unit = ()

  override def hasNext: Boolean = false

  override def next(): Mention = ???
}

object NullMentionIterator {
  val empty: NullMentionIterator = new NullMentionIterator()
}