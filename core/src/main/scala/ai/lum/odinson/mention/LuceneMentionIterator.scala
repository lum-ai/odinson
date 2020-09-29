package ai.lum.odinson.mention

import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.OdinsonScoreDoc
import ai.lum.odinson.utils.MostRecentlyUsed

import scala.annotation.tailrec

class LuceneMentionIterator(labelOpt: Option[String], nameOpt: Option[String], odinResults: OdinResults,
    factory: MentionFactory, mruIdGetter: MostRecentlyUsed[Int, LazyIdGetter]) extends MentionIterator {
  val scoreDocs: Array[OdinsonScoreDoc] = odinResults.scoreDocs
  val matchesTotal: Int = scoreDocs.foldLeft(0) { case (total, scoreDoc) => total + scoreDoc.matches.length }

  var matchesRemaining: Int = matchesTotal
  var scoreDocsIndex: Int = 0
  var matchesIndex: Int = 0

  override def hasNext: Boolean = 0 < matchesRemaining

  @tailrec
  override final def next(): Mention = {
    val scoreDoc = scoreDocs(scoreDocsIndex)
    val docIndex = scoreDoc.doc

    if (matchesIndex < scoreDoc.matches.length) {
      val odinsonMatch = scoreDoc.matches(matchesIndex)

      matchesIndex += 1
      matchesRemaining -= 1

      // TODO: double check that name == foundBy, replace?
      val idGetter = mruIdGetter.getOrNew(docIndex)
      factory.newMention(odinsonMatch, labelOpt, docIndex, scoreDoc.segmentDocId, scoreDoc.segmentDocBase, idGetter, nameOpt.getOrElse(""))
    }
    else {
      scoreDocsIndex += 1
      matchesIndex = 0
      next()
    }
  }

  override def close(): Unit = ()
}

object LuceneMentionIterator {

  def apply(labelOpt: Option[String], nameOpt: Option[String], odinResults: OdinResults, factory: MentionFactory,
      mruIdGetter: MostRecentlyUsed[Int, LazyIdGetter]): LuceneMentionIterator =
    new LuceneMentionIterator(labelOpt, nameOpt, odinResults, factory, mruIdGetter)
}
