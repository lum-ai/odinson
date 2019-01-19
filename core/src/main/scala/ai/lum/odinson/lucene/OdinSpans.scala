package ai.lum.odinson.lucene

import scala.collection.mutable.ArrayBuffer
import org.apache.lucene.search.spans.Spans

/**
 * Iterates through combinations of start/end positions per-doc.
 * Each start/end position represents a range of term positions within
 * the current document. These are enumerated in order, by increasing
 * document number, within that by increasing start position and finally
 * by increasing end position.
 *
 * (copied from lucene documentation)
 */
abstract class OdinSpans extends Spans {

  def span = Span(startPosition(), endPosition())

  def spanWithCaptures: SpanWithCaptures = SpanWithCaptures(span, namedCaptures, groupIndex, groupStride)

  def width(): Int = 0

  def namedCaptures: List[NamedCapture] = Nil

  def odinDoStartCurrentDoc() = doStartCurrentDoc()

  def odinDoCurrentSpans() = doCurrentSpans()

  // A group in this context refers to the matches that start at the same token.
  // This happens when quantifiers are used and all possible matches need to be considered,
  // but only one should be selected, based on greediness/laziness.
  // Groups are ordered, and the first one is to be prefered (i.e., the one with the lowest index)
  def groupIndex: Int = 0
  def groupStride: Int = 1

}

object OdinSpans {

  import Spans._

  def getAllSpansWithCaptures(spans: OdinSpans): Array[SpanWithCaptures] = {
    val buffer = ArrayBuffer.empty[SpanWithCaptures]
    while (spans.nextStartPosition() != NO_MORE_POSITIONS) {
      buffer += spans.spanWithCaptures
    }
    buffer.toArray
  }

}
