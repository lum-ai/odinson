package ai.lum.odinson.state

import ai.lum.odinson.EventMatch
import ai.lum.odinson.NamedCapture
import ai.lum.odinson.OdinsonMatch
import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.OdinsonScoreDoc

class EventPromoter {

  def newLabeledNamedOdinResults(namedCapture: NamedCapture, odinsonScoreDoc: OdinsonScoreDoc): LabeledNamedOdinResults = {
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

  def newFromScoreDocMatch(odinsonScoreDoc: OdinsonScoreDoc, odinsonMatch: OdinsonMatch): Array[LabeledNamedOdinResults] = {
    if (odinsonMatch.isInstanceOf[EventMatch]) {
      val eventMatch = odinsonMatch.asInstanceOf[EventMatch]
      val captures = eventMatch.namedCaptures
      val metadata = eventMatch.argumentMetadata

      assert(captures.length == metadata.length)
      captures.zip(metadata).flatMap { case (capture, metadata) =>
        val heads: Array[LabeledNamedOdinResults] =
          if (metadata.promote)
            Array(newLabeledNamedOdinResults(capture, odinsonScoreDoc))
          else
            Array.empty
        val tails: Array[LabeledNamedOdinResults] = newFromScoreDocMatch(odinsonScoreDoc, capture.capturedMatch)

        heads ++ tails
      }
    }
    else {
      odinsonMatch.namedCaptures.flatMap { capture =>
        newFromScoreDocMatch(odinsonScoreDoc, capture.capturedMatch)
      }
    }
  }

  def newFromOdinResults(odinResults: OdinResults): Array[LabeledNamedOdinResults] = {
    odinResults.scoreDocs.flatMap { odinsonScoreDoc =>
      odinsonScoreDoc.matches.flatMap { odinsonMatch =>
        newFromScoreDocMatch(odinsonScoreDoc, odinsonMatch)
      }
    }
  }

  def promoteEvents(labelOpt: Option[String], nameOpt: Option[String], odinResults: OdinResults): Array[LabeledNamedOdinResults] = {
    val labeledNamedOdinResultsSeq = LabeledNamedOdinResults(labelOpt, nameOpt, odinResults) +:
        newFromOdinResults(odinResults)

    labeledNamedOdinResultsSeq
  }
}
