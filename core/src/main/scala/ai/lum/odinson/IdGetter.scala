package ai.lum.odinson

import ai.lum.odinson.lucene.search.OdinsonIndexSearcher

// IdGetters are intended to privide access to the docid and sent id in the *original* corpus for
// a given Mentions (i.e., as opposed to the lucene ids).
trait IdGetter {
  def getDocId: String
  def getSentId: String
}

// This IdGetter is for creating a Mention when we already know the docID and sentID
// (so we don't need to **get** them), as is the case during deserialization.
class KnownIdGetter(docId: String, sentId: String) extends IdGetter {
  def getDocId: String = docId
  def getSentId: String = sentId
}

// This IdGetter delays the doc/sent id lookup until the Mention is referenced or used,
// saving computational time during extraction.
class LazyIdGetter(indexSearcher: OdinsonIndexSearcher, documentId: Int)
    extends IdGetter {
  protected lazy val document = indexSearcher.doc(documentId)
  protected lazy val docId: String = document.getField("docId").stringValue
  protected lazy val sentId: String = document.getField("sentId").stringValue

  def getDocId: String = docId

  def getSentId: String = sentId
}

object LazyIdGetter {

  def apply(indexSearcher: OdinsonIndexSearcher, docId: Int): LazyIdGetter =
    new LazyIdGetter(indexSearcher, docId)

}
