package ai.lum.odinson.lucene

import ai.lum.odinson.lucene.search._

class OdinResults(
    val totalHits: Int,
    val scoreDocs: Array[OdinsonScoreDoc],
    var maxScore: Float
) {

  def this(totalHits: Int, scoreDocs: Array[OdinsonScoreDoc]) = {
    this(totalHits, scoreDocs, Float.NaN)
  }

}

object OdinResults {

  val empty = new OdinResults(0, Array.empty, Float.NaN)

  def merge(topN: Int, shardHits: Array[OdinResults]): OdinResults = {
    merge(0, topN, shardHits, true)
  }

  def merge(
      start: Int,
      topN: Int,
      shardHits: Array[OdinResults],
      setShardIndex: Boolean
  ): OdinResults = {
    var totalHitCount = 0
    val availHitCount = shardHits.map(s => if (s == null) 0 else s.scoreDocs.length).sum
    val allHits = new Array[OdinsonScoreDoc](availHitCount)
    var currentStart = 0
    for (shardIdx <- shardHits.indices) {
      val shard = shardHits(shardIdx)
      // totalHits can be non-zero even if no hits were
      // collected, when searchAfter was used:
      totalHitCount += shard.totalHits
      if (shard.scoreDocs != null && shard.scoreDocs.length > 0) {
        if (setShardIndex) {
          // user wants us to remember shard indices
          shard.scoreDocs.foreach(_.shardIndex = shardIdx)
        }
        // collect documents
        System.arraycopy(shard.scoreDocs, 0, allHits, currentStart, shard.scoreDocs.length)
        currentStart += shard.scoreDocs.length
      }
    }
    // user wants `topN` results, but how many can we actually return?
    val size = math.min(availHitCount - start, topN)
    if (size <= 0) {
      // there's nothing to return
      empty
    } else if (start == 0 && availHitCount == size) {
      // the user wants allHits -- how about that!
      new OdinResults(totalHitCount, allHits)
    } else {
      // make array with desired docs and return it
      val hits = new Array[OdinsonScoreDoc](size)
      System.arraycopy(allHits, start, hits, 0, size)
      new OdinResults(totalHitCount, hits)
    }
  }

}
