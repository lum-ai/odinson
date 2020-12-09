package ai.lum.odinson

import ai.lum.odinson.lucene.search.OdinsonIndexSearcher

trait IdGetter {
  def getDocId: String
  def getSentId: String
}

class KnownIdGetter(docId: String, sentId: String) extends IdGetter {
  def getDocId: String = docId
  def getSentId: String = sentId
}

class LazyIdGetter(indexSearcher: OdinsonIndexSearcher, documentId: Int) extends IdGetter {
  protected lazy val document = indexSearcher.doc(documentId)
  protected lazy val docId: String = document.getField("docId").stringValue
  protected lazy val sentId: String = document.getField("sentId").stringValue

  def getDocId: String = docId

  def getSentId: String = sentId
}

object LazyIdGetter {
  def apply(indexSearcher: OdinsonIndexSearcher, docId: Int): LazyIdGetter = new LazyIdGetter(indexSearcher, docId)
}
