package ai.lum.odinson.state.sql

import ai.lum.odinson.NamedCapture
import ai.lum.odinson.OdinsonMatch

class OdinsonMatchWriteNode(odinsonMatch: OdinsonMatch, parentNode: WriteNode, val namedCapture: NamedCapture, idProvider: IdProvider) extends WriteNode(odinsonMatch, idProvider) {

  def label: String = namedCapture.label.getOrElse("")

  def name: String = namedCapture.name

  val parentNodeOpt: Option[WriteNode] = Some(parentNode)
}
