package ai.lum.odinson.state

import ai.lum.odinson.EventMatch
import ai.lum.odinson.NamedCapture
import ai.lum.odinson.OdinsonMatch
import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.OdinsonScoreDoc

class EventPromoter {

  protected def newLabeledNamedOdinResults(namedCapture: NamedCapture, odinsonScoreDoc: OdinsonScoreDoc): LabeledNamedOdinResults = {
    LabeledNamedOdinResults(
      namedCapture.label,
      Some(namedCapture.name),
      new OdinResults(
        1,
        Array(
          new OdinsonScoreDoc(
            doc = odinsonScoreDoc.doc,
            score = Float.NaN,
            shardIndex = odinsonScoreDoc.shardIndex,
            matches = Array(namedCapture.capturedMatch),
            segmentDocId = odinsonScoreDoc.segmentDocId,
            segmentDocBase = odinsonScoreDoc.segmentDocBase
          )
        )
      )
    )
  }

  protected def promoteOdinsonMatch(odinsonMatch: OdinsonMatch, odinsonScoreDoc: OdinsonScoreDoc): Array[LabeledNamedOdinResults] = {

    def promoteOdinsonMatch(odinsonMatch: OdinsonMatch): Array[LabeledNamedOdinResults] = {
      odinsonMatch match {
        case eventMatch: EventMatch =>
          val captures = eventMatch.namedCaptures
          val metadata = eventMatch.argumentMetadata

          assert(captures.length == metadata.length)
          captures.zip(metadata).flatMap { case (capture, metadata) =>
            val heads: Array[LabeledNamedOdinResults] =
              if (metadata.promote)
                Array(newLabeledNamedOdinResults(capture, odinsonScoreDoc))
              else
                Array.empty
            val tails: Array[LabeledNamedOdinResults] = promoteOdinsonMatch(capture.capturedMatch)

            heads ++ tails
          }
        case _ =>
          odinsonMatch.namedCaptures.flatMap { capture =>
            promoteOdinsonMatch(capture.capturedMatch)
          }
      }
    }

    promoteOdinsonMatch(odinsonMatch)
  }

  protected def promoteOdinResults(odinResults: OdinResults): Array[LabeledNamedOdinResults] = {
    odinResults.scoreDocs.flatMap { odinsonScoreDoc =>
      odinsonScoreDoc.matches.flatMap { odinsonMatch =>
        promoteOdinsonMatch(odinsonMatch, odinsonScoreDoc)
      }
    }
  }

  def promoteEvents(labelOpt: Option[String], nameOpt: Option[String], odinResults: OdinResults): Array[LabeledNamedOdinResults] = {
    // TODO Make this a list and convert at end to array.
    val labeledNamedOdinResultsSeq = LabeledNamedOdinResults(labelOpt, nameOpt, odinResults) +:
        promoteOdinResults(odinResults)

    labeledNamedOdinResultsSeq
  }
}
