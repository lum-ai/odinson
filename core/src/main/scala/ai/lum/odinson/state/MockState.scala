package ai.lum.odinson.state

import ai.lum.odinson.mention.Mention
import ai.lum.odinson.mention.MentionIterator
import ai.lum.odinson.mention.NullMentionIterator

object MockState extends State {
  val emptyMentionArray = new Array[Mention](0)

  def addMentions(mentions: Iterator[Mention]): Unit = ()

  def getDocIds(docBase: Int, label: String): Array[Int] = Array.emptyIntArray

  def getMentions(docBase: Int, docId: Int, label: String): Array[Mention] = emptyMentionArray

  override def getAllMentions(): MentionIterator = NullMentionIterator.empty

  def clear(): Unit = ()
}
