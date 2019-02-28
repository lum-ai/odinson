package ai.lum.odinson.lucene.search

import java.util.Arrays
import java.util.{ Map => JMap, Set => JSet }
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.join._
import org.apache.lucene.search.spans._
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search.spans._
import DocIdSetIterator._
import Spans._

class OdinsonFilteredQuery(
  val query: OdinsonQuery,
  val filter: Query,
) extends OdinsonQuery { self =>

  override def hashCode: Int = mkHash(query, filter)

  def toString(field: String): String = s"FiltereqQuery($query)"

  def getField(): String = query.getField()

  override def rewrite(reader: IndexReader): Query = {
    val rewrittenQuery = query.rewrite(reader).asInstanceOf[OdinsonQuery]
    val rewrittenFilter = filter.rewrite(reader)
    if (query != rewrittenQuery || filter != rewrittenFilter) {
      new OdinsonFilteredQuery(rewrittenQuery, rewrittenFilter)
    } else {
      super.rewrite(reader)
    }
  }

  override def createWeight(
    searcher: IndexSearcher,
    needsScores: Boolean
  ): OdinsonWeight = {
    val weight = query.createWeight(searcher, needsScores).asInstanceOf[OdinsonWeight]
    val filterWeight = filter.createWeight(searcher, needsScores)
    val terms = if (needsScores) OdinsonQuery.getTermContexts(weight) else null
    new OdinsonFilteredWeight(weight, filterWeight, searcher, terms)
  }

  class OdinsonFilteredWeight(
    val weight: OdinsonWeight,
    val filterWeight: Weight,
    searcher: IndexSearcher,
    terms: JMap[Term, TermContext]
  ) extends OdinsonWeight(self, searcher, terms) {

    def extractTerms(terms: JSet[Term]): Unit = {
      weight.extractTerms(terms)
    }

    def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = {
      weight.extractTermContexts(contexts)
    }

    def getSpans(context: LeafReaderContext, requiredPostings: SpanWeight.Postings): OdinsonSpans = {
      val terms = context.reader().terms(getField())
      if (terms == null) {
        return null // field does not exist
      }
      val spans = weight.getSpans(context, requiredPostings)
      if (spans == null) {
        return null
      }
      val scorer = filterWeight.scorer(context)
      if (scorer == null) {
        return null
      }
      val filterDisi = scorer.iterator()
      new OdinsonFilteredSpans(spans, filterDisi)
    }

  }

  class OdinsonFilteredSpans(
    val spans: OdinsonSpans,
    val filterDisi: DocIdSetIterator
  ) extends OdinsonSpans {

    // use to move to next doc considering the filter
    val conjunction: DocIdSetIterator = {
      ConjunctionDISI.intersectIterators(Arrays.asList(spans, filterDisi))
    }

    def docID(): Int = conjunction.docID()
    def cost(): Long = conjunction.cost()

    // a first start position is available in current doc for nextStartPosition
    protected var atFirstInCurrentDoc: Boolean = true

    def startPosition(): Int = if (atFirstInCurrentDoc) -1 else spans.startPosition()
    def endPosition(): Int = if (atFirstInCurrentDoc) -1 else spans.endPosition()

    override def namedCaptures: List[NamedCapture] = spans.namedCaptures
    def collect(collector: SpanCollector): Unit = spans.collect(collector)

    def nextDoc(): Int = {
      if (conjunction.nextDoc() == NO_MORE_DOCS) {
        NO_MORE_DOCS
      } else {
        toMatchDoc()
      }
    }

    def advance(target: Int): Int = {
      if (conjunction.advance(target) == NO_MORE_DOCS) {
        NO_MORE_DOCS
      } else {
        toMatchDoc()
      }
    }

    def toMatchDoc(): Int = {
      @annotation.tailrec
      def getDoc(): Int = {
        if (twoPhaseCurrentDocMatches()) {
          docID()
        } else if (conjunction.nextDoc() == NO_MORE_DOCS) {
          NO_MORE_DOCS
        } else {
          getDoc()
        }
      }
      getDoc()
    }

    def twoPhaseCurrentDocMatches(): Boolean = {
      if (spans.nextStartPosition() != NO_MORE_POSITIONS) {
        atFirstInCurrentDoc = true
        return true
      }
      false
    }

    def nextStartPosition(): Int = {
      if (atFirstInCurrentDoc) {
        atFirstInCurrentDoc = false
        return spans.startPosition()
      }
      spans.nextStartPosition()
    }

    override def asTwoPhaseIterator(): TwoPhaseIterator = {
      val tpi = spans.asTwoPhaseIterator()
      val totalMatchCost: Float =
        if (tpi != null) tpi.matchCost()
        else 0 // FIXME
      new TwoPhaseIterator(conjunction) {
        def matches(): Boolean = twoPhaseCurrentDocMatches()
        def matchCost(): Float = totalMatchCost
      }
    }

    def positionsCost(): Float = {
      // asTwoPhaseIterator never returns null (see above)
      throw new UnsupportedOperationException
    }

  }

}
