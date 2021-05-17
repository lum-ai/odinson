package ai.lum.odinson

import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.OdinsonScoreDoc
import ai.lum.odinson.utils.MostRecentlyUsed

import scala.annotation.tailrec

class MentionsIterator(
  labelOpt: Option[String],
  nameOpt: Option[String],
  odinResults: OdinResults,
  mruIdGetter: MostRecentlyUsed[Int, LazyIdGetter],
  dataGathererOpt: Option[DataGatherer]
) extends Iterator[Mention] {
  val scoreDocs: Array[OdinsonScoreDoc] = odinResults.scoreDocs

  val matchesTotal: Int = scoreDocs.foldLeft(0) { case (total, scoreDoc) =>
    total + scoreDoc.matches.length
  }

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
      new Mention(
        odinsonMatch,
        labelOpt,
        docIndex,
        scoreDoc.segmentDocId,
        scoreDoc.segmentDocBase,
        idGetter,
        nameOpt.getOrElse(""),
        dataGathererOpt
      )
    } else {
      scoreDocsIndex += 1
      matchesIndex = 0
      next()
    }
  }

}

object MentionsIterator {
  val emptyMentionIterator = Iterator[Mention]()

  def apply(
    labelOpt: Option[String],
    nameOpt: Option[String],
    odinResults: OdinResults,
    mruIdGetter: MostRecentlyUsed[Int, LazyIdGetter],
    dataGathererOpt: Option[DataGatherer]
  ): MentionsIterator =
    new MentionsIterator(labelOpt, nameOpt, odinResults, mruIdGetter, dataGathererOpt)

  def concatenate(iterators: Seq[Iterator[Mention]]): Iterator[Mention] = {
    iterators.foldLeft(emptyMentionIterator)(_ ++ _)
  }

}
