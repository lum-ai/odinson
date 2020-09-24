package ai.lum.odinson.state

import ai.lum.odinson.EventMatch
import ai.lum.odinson.NamedCapture
import ai.lum.odinson.OdinsonMatch
import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.OdinsonScoreDoc


abstract class EventPromoter {
  def promoteEvents(labeledNamedOdinResults: LabeledNamedOdinResults): Array[LabeledNamedOdinResults]

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

  protected def promoteOdinsonMatch(odinsonMatch: OdinsonMatch, odinsonScoreDoc: OdinsonScoreDoc): Array[LabeledNamedOdinResults]

  protected def promoteOdinResults(odinResults: OdinResults): Array[LabeledNamedOdinResults] = {
    odinResults.scoreDocs.flatMap { odinsonScoreDoc =>
      odinsonScoreDoc.matches.flatMap { odinsonMatch =>
        promoteOdinsonMatch(odinsonMatch, odinsonScoreDoc)
      }
    }
  }
}

class FlatEventPromoter extends EventPromoter {

  protected def promoteOdinsonMatch(odinsonMatch: OdinsonMatch, odinsonScoreDoc: OdinsonScoreDoc): Array[LabeledNamedOdinResults] = {
    val result = odinsonMatch match {
      case eventMatch: EventMatch =>
        // Turn this back on for paranoia mode.
        // {
        //  val namesAndArgumentMetadatas = eventMatch.argumentMetadata.groupBy(_.name)
        //  namesAndArgumentMetadatas.foreach { case (name, argumentMetadatas) =>
        //    // Those with the same name should all be the same.
        //    argumentMetadatas.distinct.length == 1
        //  }
        // }
        val namesToPromote: Set[String] = eventMatch.argumentMetadata
            .map(_.name)
            .toSet
        val namedCapturesToPromote = eventMatch.namedCaptures.filter { namedCapture =>
          namesToPromote.contains(namedCapture.name)
        }
        val labeledNamedOdinResults = namedCapturesToPromote.map { namedCapture =>
          newLabeledNamedOdinResults(namedCapture, odinsonScoreDoc)
        }

        labeledNamedOdinResults
      case _ => Array.empty[LabeledNamedOdinResults]
    }

    result
  }

  override def promoteEvents(labeledNamedOdinResults: LabeledNamedOdinResults): Array[LabeledNamedOdinResults] = {
    // Return the one that was passed in plus any others that result from promotions.
    labeledNamedOdinResults +: promoteOdinResults(labeledNamedOdinResults.odinResults)
  }
}

class HierarchicalEventPromoter extends EventPromoter {

  protected def promoteOdinsonMatch(odinsonMatch: OdinsonMatch, odinsonScoreDoc: OdinsonScoreDoc): Array[LabeledNamedOdinResults] = {

    def promoteOdinsonMatch(odinsonMatch: OdinsonMatch): Array[LabeledNamedOdinResults] = {
      val result = odinsonMatch match {
        // This is only ever considered in the case of an EventMatch.
        case eventMatch: EventMatch =>
          // val names = eventMatch.namedCaptures.map(_.name)
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
            val promote = nameToPromote.getOrElse(capture.name, false)
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

  def promoteEvents(labeledNamedOdinResults: LabeledNamedOdinResults): Array[LabeledNamedOdinResults] = {
    // TODO Make this a list and convert at end to array.
    val labeledNamedOdinResultsSeq = labeledNamedOdinResults +:
        promoteOdinResults(labeledNamedOdinResults.odinResults)

    labeledNamedOdinResultsSeq
  }
}
