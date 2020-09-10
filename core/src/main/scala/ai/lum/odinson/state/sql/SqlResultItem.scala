package ai.lum.odinson.state.sql

import ai.lum.odinson.NamedCapture
import ai.lum.odinson.StateMatch
import ai.lum.odinson.state.ResultItem

import scala.collection.mutable.ArrayBuffer

object SqlResultItem {

  def toWriteNodes(resultItem: ResultItem, idProvider: IdProvider): IndexedSeq[WriteNode] = {
    val arrayBuffer = new ArrayBuffer[WriteNode]()

    new ResultItemWriteNode(resultItem, idProvider).flatten(arrayBuffer)
    arrayBuffer.toIndexedSeq
  }

  def fromReadNodes(docBase: Int, docId: Int, label: String, readItems: ArrayBuffer[ReadNode]): ResultItem = {
    val iterator = readItems.reverseIterator
    val first = iterator.next

    def findNamedCaptures(childCount: Int): Array[NamedCapture] = {
      val namedCaptures = if (childCount == 0) Array.empty[NamedCapture] else new Array[NamedCapture](childCount)
      var count = 0

      while (count < childCount) {
        val readNode = iterator.next

        count += 1
        // These go in backwards because of reverse.
        namedCaptures(childCount - count) = NamedCapture(readNode.name, if (readNode.childLabel.nonEmpty) Some(readNode.childLabel) else None,
          StateMatch(readNode.start, readNode.end, findNamedCaptures(readNode.childCount)))
      }
      namedCaptures
    }

    ResultItem(docBase, docId, first.docIndex, label, first.name,
      StateMatch(first.start, first.end, findNamedCaptures(first.childCount)))
  }
}
