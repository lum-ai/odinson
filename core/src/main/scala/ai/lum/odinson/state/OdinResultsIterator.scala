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

case class LabeledNamedOdinResults(labelOpt: Option[String], nameOpt: Option[String], odinResults: OdinResults)

class SuperOdinResultsIterator(labeledNamedOdinResultsSeq: Array[LabeledNamedOdinResults]) extends Iterator[ResultItem] {
  protected val iterator: Iterator[ResultItem] =
      labeledNamedOdinResultsSeq.foldLeft(Iterator[ResultItem]())(_ ++ new OdinResultsIterator(_))

  override def hasNext: Boolean = iterator.hasNext

  override def next(): ResultItem = iterator.next()
}

class OdinResultsIterator(labelOpt: Option[String], nameOpt: Option[String], odinResults: OdinResults) extends Iterator[ResultItem] {

  def this(labeledNamedOdinResults: LabeledNamedOdinResults) =
      this(labeledNamedOdinResults.labelOpt, labeledNamedOdinResults.nameOpt, labeledNamedOdinResults.odinResults)

  val scoreDocs: Array[OdinsonScoreDoc] = odinResults.scoreDocs
  val matchesTotal: Int = scoreDocs.foldLeft(0) { case (total, scoreDoc) => total + scoreDoc.matches.length }

  var matchesRemaining: Int = matchesTotal
  var scoreDocsIndex: Int = 0
  var matchesIndex: Int = 0

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
