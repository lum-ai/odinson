package ai.lum.odinson.state.sql

case class ReadNode(docIndex: Int, name: String, id: Int, parentId: Int, childCount: Int, childLabel: String, start: Int, end: Int)
