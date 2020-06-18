package ai.lum.odinson.utils

import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.OdinsonScoreDoc

import scala.annotation.tailrec

class OdinResultsIterator(odinResults: OdinResults, label: String) extends Iterator[(Int, Int, String, Int, Int) ] {
  val scoreDocs: Array[OdinsonScoreDoc] = odinResults.scoreDocs
  val matchesTotal: Int = scoreDocs.foldLeft(0) { case (total, scoreDoc) => total + scoreDoc.matches.length }

  var matchesRemaining: Int = matchesTotal
  var scoreDocsIndex: Int = 0
  var matchesIndex: Int = 0

  override def hasNext: Boolean = 0 < matchesRemaining

  @tailrec
  override final def next(): (Int, Int, String, Int, Int) = {
    val scoreDoc = scoreDocs(scoreDocsIndex)

    if (matchesIndex < scoreDoc.matches.length) {
      val odinsonMatch = scoreDoc.matches(matchesIndex)

      matchesIndex += 1
      matchesRemaining -= 1
      (scoreDoc.segmentDocBase, scoreDoc.segmentDocId, label, odinsonMatch.start, odinsonMatch.end)
    }
    else {
      scoreDocsIndex += 1
      matchesIndex = 0
      next()
    }
  }
}

object OdinResultsIterator {
  def apply(odinResults: OdinResults, label: String): OdinResultsIterator = new OdinResultsIterator(odinResults, label)
}