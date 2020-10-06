package ai.lum.odinson.mention

import ai.lum.odinson.lucene.search.OdinsonIndexSearcher

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
