package ai.lum.odinson.state.sql

import ai.lum.odinson.state.ResultItem

class ResultItemWriteNode(val resultItem: ResultItem, idProvider: IdProvider) extends WriteNode(resultItem.odinsonMatch, idProvider) {

  def label: String = resultItem.label

  def name: String = resultItem.name

  def parentNodeOpt: Option[WriteNode] = None
}
