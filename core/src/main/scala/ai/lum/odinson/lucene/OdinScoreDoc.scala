package ai.lum.odinson.lucene

import org.apache.lucene.search.ScoreDoc

class OdinScoreDoc(
    doc: Int,
    score: Float,
    shardIndex: Int = -1,
    var matches: Array[SpanWithCaptures] = Array.empty
) extends ScoreDoc(doc, score, shardIndex)
