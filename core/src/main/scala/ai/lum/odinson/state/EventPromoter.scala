package ai.lum.odinson.state

import ai.lum.odinson.EventMatch
import ai.lum.odinson.NamedCapture
import ai.lum.odinson.OdinsonMatch
import ai.lum.odinson.StateMatch
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
        // This is only ever considered in the case of an EventMatch.
        case eventMatch: EventMatch =>
          val names = eventMatch.namedCaptures.map(_.name)
          // It is required that there are no duplicate names in the namedCaptures.
          // This is not true!
          // require(names.distinct.size == eventMatch.namedCaptures.length)
          // Because of this, the namedCapture can be stored more than once in the state.
          val nameToPromote = eventMatch.argumentMetadata
              .groupBy(_.name)
              .map { case (name, argumentMetadatums) =>
                // If one doesn't need to be promoted, must it be in the state already?
                val promote = argumentMetadatums.forall(_.promote)
                name -> promote
              }

          eventMatch.namedCaptures.flatMap { capture =>
            // Is it possible that not all namedArguments have ArgumentMetadata?
            val promote = nameToPromote.get(capture.name).getOrElse(false)
            val heads: Array[LabeledNamedOdinResults] =
              if (promote)
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
