package ai.lum.odinson.state.sql

import ai.lum.odinson.OdinsonMatch

import scala.collection.mutable.ArrayBuffer

abstract class WriteNode(val odinsonMatch: OdinsonMatch, idProvider: IdProvider) {
  val childNodes: Array[WriteNode] = {
    odinsonMatch.namedCaptures.map { namedCapture =>
      new OdinsonMatchWriteNode(namedCapture.capturedMatch, this, namedCapture, idProvider)
    }
  }
  val id: Int = idProvider.next

  def label: String

  def name: String

  def parentNodeOpt: Option[WriteNode]

  def flatten(writeNodes: ArrayBuffer[WriteNode]): Unit = {
    childNodes.foreach(_.flatten(writeNodes))
    writeNodes += this
  }

  def parentId: Int = parentNodeOpt.map(_.id).getOrElse(-1)

  def start: Int = odinsonMatch.start

  def end: Int = odinsonMatch.end
}
