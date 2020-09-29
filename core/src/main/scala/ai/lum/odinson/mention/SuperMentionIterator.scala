package ai.lum.odinson.mention

class SuperMentionIterator(iterator: MentionIterator) extends MentionIterator {
  override def close(): Unit = iterator.close()

  override def hasNext: Boolean = iterator.hasNext

  override def next(): Mention = iterator.next()
}
