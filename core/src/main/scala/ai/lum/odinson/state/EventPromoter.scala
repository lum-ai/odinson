package ai.lum.odinson.state

import ai.lum.odinson.EventMatch
import ai.lum.odinson.NamedCapture
import ai.lum.odinson.OdinsonMatch
import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.OdinsonScoreDoc

class EventPromoter {

  protected def countChildren(odinsonMatch: OdinsonMatch): Int = {
    val count = odinsonMatch.namedCaptures.foldLeft(0) { case (total, namedCapture) =>
      total + 1 + countChildren(namedCapture.capturedMatch)
    }

    count
  }

  protected def newLabeledNamedOdinResults(namedCapture: NamedCapture, odinsonScoreDoc: OdinsonScoreDoc): LabeledNamedOdinResults = {
    val odinsonMatch = namedCapture.capturedMatch
    val childCount = countChildren(odinsonMatch)

    LabeledNamedOdinResults(
      namedCapture.label,
      Some(namedCapture.name),
      new OdinResults(
        1 + childCount,
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
      val result = odinsonMatch match {
        case eventMatch: EventMatch =>
          val captures = eventMatch.namedCaptures
          val metadata = eventMatch.argumentMetadata

          if (captures.length != metadata.length)
            println("What is wrong?")

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

      result
    }
    val result = promoteOdinsonMatch(odinsonMatch)

    result
  }

  protected def promoteOdinResults(odinResults: OdinResults): Array[LabeledNamedOdinResults] = {
    odinResults.scoreDocs.flatMap { odinsonScoreDoc =>
      odinsonScoreDoc.matches.flatMap { odinsonMatch =>
        promoteOdinsonMatch(odinsonMatch, odinsonScoreDoc)
      }
    }
  }

  def promoteEvents(labeledNamedOdinResults: LabeledNamedOdinResults): Array[LabeledNamedOdinResults] = {
    // TODO Make this a list and convert at end to array.
    val labeledNamedOdinResultsSeq = labeledNamedOdinResults +:
        promoteOdinResults(labeledNamedOdinResults.odinResults)

    labeledNamedOdinResultsSeq
  }
}
