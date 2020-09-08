package ai.lum.odinson.state

import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.OdinsonScoreDoc

import scala.annotation.tailrec

/**
  *
  * @param labelOpt comes from the Extractor
  * @param nameOpt also comes form the Extractor
  * @param odinResults
  */

/* Notes
      scoreDoc <- odinResults.scoreDocs
      document = doc(scoreDoc.doc)
      docId = document.getField("docId").stringValue
      sentId = document.getField("sentId").stringValue
      odinsonMatch <- scoreDoc.matches
      mention = mentionFactory.newMention(odinsonMatch, extractor.label, scoreDoc.doc, scoreDoc.segmentDocId, scoreDoc.segmentDocBase, docId, sentId, extractor.name)
 */
class OdinResultsIterator(labelOpt: Option[String], nameOpt: Option[String], odinResults: OdinResults) extends Iterator[ResultItem] {
  val scoreDocs: Array[OdinsonScoreDoc] = odinResults.scoreDocs
  val matchesTotal: Int = scoreDocs.foldLeft(0) { case (total, scoreDoc) => total + scoreDoc.matches.length }
  val totalHits = odinResults.totalHits

  assert(matchesTotal == totalHits) // Double check temporarily

  var matchesRemaining: Int = matchesTotal
  var scoreDocsIndex: Int = 0
  var matchesIndex: Int = 0

  override def size(): Int = matchesTotal

  override def hasNext: Boolean = 0 < matchesRemaining

  @tailrec
  override final def next(): ResultItem = {
    val scoreDoc = scoreDocs(scoreDocsIndex)
    val docIndex = scoreDoc.doc

    if (matchesIndex < scoreDoc.matches.length) {
      val odinsonMatch = scoreDoc.matches(matchesIndex)

      matchesIndex += 1
      matchesRemaining -= 1
      ResultItem(
        scoreDoc.segmentDocBase, scoreDoc.segmentDocId, docIndex,
        labelOpt.getOrElse(""), nameOpt.getOrElse(""), odinsonMatch
      )
    }
    else {
      scoreDocsIndex += 1
      matchesIndex = 0
      next()
    }
  }
}

object OdinResultsIterator {
  def apply(labelOpt: Option[String], nameOpt: Option[String], odinResults: OdinResults): OdinResultsIterator = new OdinResultsIterator(labelOpt, nameOpt, odinResults)
}