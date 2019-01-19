package ai.lum.odinson.lucene

import org.apache.lucene.util.PriorityQueue

class OdinResults(
    val totalHits: Int,
    val scoreDocs: Array[OdinScoreDoc],
    var maxScore: Float
) {

  def this(totalHits: Int, scoreDocs: Array[OdinScoreDoc]) = {
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
    mergeAux(start, topN, shardHits, setShardIndex)
  }

  private def mergeAux(
      start: Int,
      size: Int,
      shardHits: Array[OdinResults],
      setShardIndex: Boolean
  ): OdinResults = {

    val queue: PriorityQueue[ShardRef] = new ScoreMergeSortQueue(shardHits)

    var totalHitCount = 0
    var availHitCount = 0
    var maxScore = Float.MinValue
    for (shardIDX <- shardHits.indices) {
      val shard = shardHits(shardIDX)
      // totalHits can be non-zero even if no hits were
      // collected, when searchAfter was used:
      totalHitCount += shard.totalHits
      if (shard.scoreDocs != null && shard.scoreDocs.length > 0) {
        availHitCount += shard.scoreDocs.length
        queue.add(new ShardRef(shardIDX, ! setShardIndex))
        maxScore = math.max(maxScore, shard.maxScore)
      }
    }

    if (availHitCount == 0) {
      maxScore = Float.NaN
    }

    // https://github.com/apache/lucene-solr/blob/branch_6_6/lucene/core/src/java/org/apache/lucene/search/TopDocs.java#L288
    var hits: Array[OdinScoreDoc] = Array.empty
    if (availHitCount > start) {
      hits = new Array[OdinScoreDoc](math.min(size, availHitCount - start))
      val requestedResultWindow = start + size
      val numIterOnHits = math.min(availHitCount, requestedResultWindow)
      var hitUpto = 0
      while (hitUpto < numIterOnHits) {
        assert(queue.size() > 0)
        val ref = queue.top()
        val hit = shardHits(ref.shardIndex).scoreDocs(ref.hitIndex)
        ref.hitIndex += 1
        if (setShardIndex) {
          // caller asked us to record shardIndex (index of the TopDocs array)
          // this hit is coming from:
          hit.shardIndex = ref.shardIndex
        } else if (hit.shardIndex == -1) {
          throw new IllegalArgumentException("setShardIndex is false but TopDocs[" + ref.shardIndex + "].scoreDocs[" + (ref.hitIndex-1) + "] is not set")
        }

        if (hitUpto >= start) {
          hits(hitUpto - start) = hit
        }

        hitUpto += 1

        if (ref.hitIndex < shardHits(ref.shardIndex).scoreDocs.length) {
          // Not done with this these TopDocs yet:
          queue.updateTop()
        } else {
          queue.pop()
        }

      }

    }

    new OdinResults(totalHitCount, hits, maxScore)
  }

  def tieBreakLessThan(first: ShardRef, firstDoc: OdinScoreDoc, second: ShardRef, secondDoc: OdinScoreDoc): Boolean = {
    val firstShardIndex = first.getShardIndex(firstDoc)
    val secondShardIndex = second.getShardIndex(secondDoc)
    // Tie break: earlier shard wins
    if (firstShardIndex< secondShardIndex) {
      true
    } else if (firstShardIndex > secondShardIndex) {
      false
    } else {
      // Tie break in same shard: resolve however the
      // shard had resolved it:
      assert(first.hitIndex != second.hitIndex)
      first.hitIndex < second.hitIndex
    }
  }

  class ShardRef(val shardIndex: Int, val useScoreDocIndex: Boolean, var hitIndex: Int = 0) {
    def getShardIndex(scoreDoc: OdinScoreDoc): Int = {
      if (useScoreDocIndex) {
        require(
          scoreDoc.shardIndex >= 0,
          s"setShardIndex is false but TopDocs[$shardIndex].scoreDocs[$hitIndex] is not set"
        )
        scoreDoc.shardIndex
      } else {
        // NOTE: we don't assert that shardIndex is -1 here,
        // because caller could in fact have set it but asked us to ignore it now
        shardIndex
      }
    }
  }

  class ScoreMergeSortQueue(results: Array[OdinResults])
  extends PriorityQueue[ShardRef](results.length) {

    val shardHits: Array[Array[OdinScoreDoc]] = results.map(_.scoreDocs)

    def lessThan(first: ShardRef, second: ShardRef): Boolean = {
      assert(first != second)
      val firstScoreDoc = shardHits(first.shardIndex)(first.hitIndex)
      val secondScoreDoc = shardHits(second.shardIndex)(second.hitIndex)
      if (firstScoreDoc.score < secondScoreDoc.score) {
        false
      } else if (firstScoreDoc.score > secondScoreDoc.score) {
        true
      } else {
        tieBreakLessThan(first, firstScoreDoc, second, secondScoreDoc)
      }
    }

  }

}
