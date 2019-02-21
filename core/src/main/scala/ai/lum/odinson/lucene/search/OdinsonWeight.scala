package ai.lum.odinson.lucene.search

import java.util.{ Map => JMap }
import scala.collection.JavaConverters._
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import org.apache.lucene.search.similarities._
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search.spans._

/**
 * The Weight interface provides an internal representation of the Query
 * so that it can be reused. Any IndexSearcher dependent state should be
 * stored in the Weight implementation, not in the Query class.
 *
 * (copied from lucene documentation)
 */
abstract class OdinsonWeight(
    val query: OdinsonQuery,
    val searcher: IndexSearcher,
    val termContexts: JMap[Term, TermContext]
) extends Weight(query) {

  import SpanWeight._

  val similarity: Similarity = searcher.getSimilarity(termContexts != null)
  val simWeight: Similarity.SimWeight = OdinsonWeight.buildSimWeight(query, searcher, termContexts, similarity)

  def extractTermContexts(contexts: JMap[Term, TermContext]): Unit
  def getSpans(ctx: LeafReaderContext, requiredPostings: Postings): OdinsonSpans

  def getValueForNormalization(): Float = {
    if (simWeight == null) 1.0f else simWeight.getValueForNormalization()
  }

  def normalize(queryNorm: Float, boost: Float): Unit = {
    if (simWeight != null) simWeight.normalize(queryNorm, boost)
  }

  def scorer(context: LeafReaderContext): OdinsonScorer = {
    val spans = getSpans(context, Postings.POSITIONS)
    if (spans == null) return null
    val docScorer = getSimScorer(context)
    new OdinsonScorer(this, spans, docScorer)
  }

  def getSimScorer(context: LeafReaderContext): Similarity.SimScorer = {
    if (simWeight == null) null else similarity.simScorer(simWeight, context)
  }

  def explain(context: LeafReaderContext, doc: Int): Explanation = {
    val _scorer = scorer(context)
    if (_scorer != null) {
      val newDoc = _scorer.iterator().advance(doc)
      if (newDoc == doc) {
        val freq = _scorer.sloppyFreq()
        val docScorer = similarity.simScorer(simWeight, context)
        val freqExplanation = Explanation.`match`(freq, "phraseFreq=" + freq)
        val scoreExplanation = docScorer.explain(doc, freqExplanation)
        return Explanation.`match`(scoreExplanation.getValue(),
          "weight("+getQuery()+" in "+doc+") [" + similarity.getClass().getSimpleName() + "], result of:",
          scoreExplanation)
      }
    }
    Explanation.noMatch("no matching term")
  }

}

object OdinsonWeight {

  def buildSimWeight(
      query: OdinsonQuery,
      searcher: IndexSearcher,
      termContexts: JMap[Term, TermContext],
      similarity: Similarity
  ): Similarity.SimWeight = {
    if (termContexts == null || termContexts.size() == 0 || query.getField() == null) return null
    val termStats = new Array[TermStatistics](termContexts.size())
    var i = 0
    for (term <- termContexts.keySet().iterator().asScala) {
      termStats(i) = searcher.termStatistics(term, termContexts.get(term))
      i += 1
    }
    val collectionStats = searcher.collectionStatistics(query.getField())
    similarity.computeWeight(collectionStats, termStats: _*)
  }

}
