package ai.lum.odinson.lucene.search

import org.apache.lucene.search.ScoreDoc
import ai.lum.odinson._

class OdinsonScoreDoc(
  doc: Int,
  score: Float,
  shardIndex: Int = -1,
  var matches: Array[OdinsonMatch] = Array.empty,
  var segmentDocId: Int = -1,
  var segmentDocBase: Int = -1
) extends ScoreDoc(doc, score, shardIndex)
