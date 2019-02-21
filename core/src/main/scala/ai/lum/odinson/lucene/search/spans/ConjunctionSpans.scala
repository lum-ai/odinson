package ai.lum.odinson.lucene.search.spans

import java.util.Arrays
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson.lucene._

/**
 * This is a port of org.apache.lucene.search.spans.ConjunctionSpans
 * We had to do this because the original is not a public class.
 */
trait ConjunctionSpans extends OdinSpans {

  import DocIdSetIterator._

  protected var matchStart: Int = -1
  protected var matchEnd: Int = -1

  def startPosition(): Int = if (atFirstInCurrentDoc) -1 else matchStart
  def endPosition(): Int = if (atFirstInCurrentDoc) -1 else matchEnd

  // a subclass needs to implement these two methods
  def subSpans: Array[OdinSpans]
  def twoPhaseCurrentDocMatches(): Boolean

  // a first start position is available in current doc for nextStartPosition
  protected var atFirstInCurrentDoc: Boolean = true

  // one subspans exhausted in current doc
  protected var oneExhaustedInCurrentDoc: Boolean = false

  // use to move to next doc with all clauses
  val conjunction: DocIdSetIterator = {
    ConjunctionDISI.intersectSpans(Arrays.asList(subSpans:_*))
  }

  def cost(): Long = conjunction.cost()

  def docID(): Int = conjunction.docID()

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
    oneExhaustedInCurrentDoc = false
    getDoc()
  }

  def collect(collector: SpanCollector): Unit = {
    for (spans <- subSpans) spans.collect(collector)
  }

  override def asTwoPhaseIterator(): TwoPhaseIterator = {
    var totalMatchCost = 0f
    // Compute the matchCost as the total matchCost/positionsCost of the subSpans.
    for (s <- subSpans) {
      val tpi = s.asTwoPhaseIterator()
      if (tpi != null) {
        totalMatchCost += tpi.matchCost()
      } else {
        totalMatchCost += s.positionsCost()
      }
    }
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
