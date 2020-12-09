package ai.lum.odinson

import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.{OdinsonIndexSearcher, OdinsonScoreDoc}
import ai.lum.odinson.utils.MostRecentlyUsed
import com.typesafe.config.Config

import scala.annotation.tailrec

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


trait MentionFactory {

  class OdinMentionsIterator(labelOpt: Option[String], nameOpt: Option[String], odinResults: OdinResults, factory: MentionFactory, mruIdGetter: MostRecentlyUsed[Int, LazyIdGetter]) extends Iterator[Mention] {
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
  }

  def mentionsIterator(labelOpt: Option[String], nameOpt: Option[String], odinResults: OdinResults, mruIdGetter: MostRecentlyUsed[Int, LazyIdGetter]): OdinMentionsIterator = {
    new OdinMentionsIterator(labelOpt, nameOpt, odinResults, this, mruIdGetter)
  }

  // If you are implementing a custom MentionFactory, this is the primary method
  // you need to implement.
  def newMention(
    odinsonMatch: OdinsonMatch,
    label: Option[String],
    luceneDocId: Int,
    luceneSegmentDocId: Int,
    luceneSegmentDocBase: Int,
    idGetter: IdGetter,
    foundBy: String,
    arguments: Map[String, Array[Mention]],
  ): Mention

  def newMention(
    odinsonMatch: OdinsonMatch,
    label: Option[String],
    luceneDocId: Int,
    luceneSegmentDocId: Int,
    luceneSegmentDocBase: Int,
    idGetter: IdGetter,
    foundBy: String
  ): Mention = {
    val arguments = mkArguments(odinsonMatch, label, luceneDocId, luceneSegmentDocId, luceneSegmentDocBase, idGetter, foundBy)
    newMention(odinsonMatch, label, luceneDocId, luceneSegmentDocId, luceneSegmentDocBase, idGetter, foundBy, arguments)
  }


  /** A map from argument name to a sequence of matches.
    *
    * The value of the map is a sequence because there are events
    * that can have several arguments with the same name.
    * For example, in the biodomain, Binding may have several themes.
    */
  def mkArguments(
    odinsonMatch: OdinsonMatch,
    label: Option[String],
    luceneDocId: Int,
    luceneSegmentDocId: Int,
    luceneSegmentDocBase: Int,
    idGetter: IdGetter,
    foundBy: String,
  ): Map[String, Array[Mention]] = {
    odinsonMatch
      .namedCaptures
      // get all the matches for each name
      .groupBy(_.name)
      .transform { (name, captures) =>
        // Make a mention from each match in the named capture
        captures.map { capture =>
          newMention(
            capture.capturedMatch,
            capture.label,
            luceneDocId,
            luceneSegmentDocId,
            luceneSegmentDocBase,
            idGetter,
            // we mark the captures as matched by the same rule as the whole match
            // todo: FoundBy handler
            // todo: add foundBy info to state somehow
            foundBy)
        }
      }
  }

}

object MentionFactory {
  def fromConfig(config: Config): MentionFactory = new DefaultMentionFactory
}
