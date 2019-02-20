package ai.lum.odinson.lucene.search

import org.apache.lucene.search.ScoreDoc
import ai.lum.odinson.lucene._

class OdinsonScoreDoc(
    doc: Int,
    score: Float,
    shardIndex: Int = -1,
    var matches: Array[SpanWithCaptures] = Array.empty
) extends ScoreDoc(doc, score, shardIndex)
