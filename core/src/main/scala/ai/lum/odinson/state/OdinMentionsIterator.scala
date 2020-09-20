package ai.lum.odinson.state

import ai.lum.odinson.{IdGetter, LazyIdGetter, Mention, MentionFactory}
import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.OdinsonScoreDoc
import ai.lum.odinson.utils.MostRecentlyUsed

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
//class OdinMentionsIterator(labelOpt: Option[String], nameOpt: Option[String], odinResults: OdinResults, factory: MentionFactory, mruIdGetter: MostRecentlyUsed[Int, LazyIdGetter]) extends Iterator[Mention] {
//  val scoreDocs: Array[OdinsonScoreDoc] = odinResults.scoreDocs
//  val matchesTotal: Int = scoreDocs.foldLeft(0) { case (total, scoreDoc) => total + scoreDoc.matches.length }
//
//  var matchesRemaining: Int = matchesTotal
//  var scoreDocsIndex: Int = 0
//  var matchesIndex: Int = 0
//
//  override def hasNext: Boolean = 0 < matchesRemaining
//
//  @tailrec
//  override final def next(): Mention = {
//    val scoreDoc = scoreDocs(scoreDocsIndex)
//    val docIndex = scoreDoc.doc
//
//    if (matchesIndex < scoreDoc.matches.length) {
//      val odinsonMatch = scoreDoc.matches(matchesIndex)
//
//      matchesIndex += 1
//      matchesRemaining -= 1
//
//      // TODO: double check that name == foundBy, replace?
//      val idGetter = mruIdGetter.getOrNew(docIndex)
//      factory.newMention(odinsonMatch, labelOpt, docIndex, scoreDoc.segmentDocId, scoreDoc.segmentDocBase, idGetter, nameOpt.getOrElse(""))
//    }
//    else {
//      scoreDocsIndex += 1
//      matchesIndex = 0
//      next()
//    }
//  }
//}

object OdinMentionsIterator {
  val emptyMentionIterator = Iterator[Mention]()
//  def apply(labelOpt: Option[String], nameOpt: Option[String], odinResults: OdinResults, factory: MentionFactory, mruIdGetter:MostRecentlyUsed[Int, LazyIdGetter]): OdinMentionsIterator = new OdinMentionsIterator(labelOpt, nameOpt, odinResults, factory, mruIdGetter)

  def concatenate(iterators: Seq[Iterator[Mention]]): Iterator[Mention] = {
    iterators.foldLeft(emptyMentionIterator)(_ ++ _)
  }
}