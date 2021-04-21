package ai.lum.odinson.lucene.search

import java.util.{ Map => JMap, Set => JSet }

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson._
import ai.lum.odinson.lucene.util._
import ai.lum.odinson.lucene.search.spans._
import ai.lum.odinson.digraph._
import ai.lum.odinson.serialization.UnsafeSerializer
import ai.lum.odinson.state.State

case class ArgumentQuery(
  name: String,
  label: Option[String],
  min: Int,
  max: Option[Int],
  promote: Boolean, // capture mention on-the-fly if not already captured
  fullTraversal: FullTraversalQuery
) {

  def setState(stateOpt: Option[State]): Unit = {
    fullTraversal.setState(stateOpt)
  }

  // TODO: more informative representation (for this and other Queries)
  def toString(field: String): String = {
    val traversal = fullTraversal.toString(field)
    s"ArgumentQuery($name, $label, $min, $max, $promote, $traversal)"
  }

  def createWeight(
    searcher: IndexSearcher,
    needsScores: Boolean
  ): ArgumentWeight = {
    val fullTraversalWeights = fullTraversal.createWeight(searcher, needsScores)
    ArgumentWeight(name, label, min, max, promote, fullTraversalWeights)
  }

  def rewrite(reader: IndexReader): ArgumentQuery = {
    val rewrittenTraversal = fullTraversal.rewrite(reader)
    if (rewrittenTraversal != fullTraversal) {
      ArgumentQuery(name, label, min, max, promote, rewrittenTraversal)
    } else {
      this
    }
  }

}

case class ArgumentWeight(
  name: String,
  label: Option[String],
  min: Int,
  max: Option[Int],
  promote: Boolean,
  fullTraversal: FullTraversalWeight
) {

  def getSpans(
    context: LeafReaderContext,
    requiredPostings: SpanWeight.Postings
  ): ArgumentSpans = {
    val fullTraversalSpans = fullTraversal.getSpans(context, requiredPostings)
    if (fullTraversalSpans == null) return null
    ArgumentSpans(name, label, min, max, promote, fullTraversalSpans)
  }

  def subWeights: List[OdinsonWeight] = {
    fullTraversal.subWeights
  }

}

case class ArgumentSpans(
  name: String,
  label: Option[String],
  min: Int,
  max: Option[Int],
  promote: Boolean,
  fullTraversal: FullTraversalSpans
) {

  def subSpans: List[OdinsonSpans] = {
    fullTraversal.subSpans
  }

}

class OdinsonEventQuery(
  val trigger: OdinsonQuery,
  val requiredArguments: List[ArgumentQuery],
  val optionalArguments: List[ArgumentQuery],
  val dependenciesField: String,
  val sentenceLengthField: String
) extends OdinsonQuery { self =>

  override def hashCode: Int = (trigger, requiredArguments, optionalArguments, dependenciesField).##

  override def setState(stateOpt: Option[State]): Unit = {
    trigger.setState(stateOpt)
    requiredArguments.foreach(_.setState(stateOpt))
    optionalArguments.foreach(_.setState(stateOpt))
  }

  def toString(field: String): String = {
    val triggerStr = trigger.toString(field)
    val reqStr = requiredArguments.map(_.toString(field)).mkString(",")
    val optStr = optionalArguments.map(_.toString(field)).mkString(",")
    s"Event($triggerStr, [$reqStr], [$optStr])"
  }

  def getField(): String = trigger.getField()

  override def rewrite(reader: IndexReader): Query = {
    val rewrittenTrigger = trigger.rewrite(reader).asInstanceOf[OdinsonQuery]
    val rewrittenRequiredArguments = requiredArguments.map(_.rewrite(reader))
    val rewrittenOptionalArguments = optionalArguments.map(_.rewrite(reader))
    if (
      trigger != rewrittenTrigger || requiredArguments != rewrittenRequiredArguments || optionalArguments != rewrittenOptionalArguments
    ) {
      new OdinsonEventQuery(
        rewrittenTrigger,
        rewrittenRequiredArguments,
        rewrittenOptionalArguments,
        dependenciesField,
        sentenceLengthField
      )
    } else {
      super.rewrite(reader)
    }
  }

  override def createWeight(
    searcher: IndexSearcher,
    needsScores: Boolean
  ): OdinsonWeight = {
    val triggerWeight = trigger
      .createWeight(searcher, needsScores)
      .asInstanceOf[OdinsonWeight]
    val required = requiredArguments.map(_.createWeight(searcher, needsScores))
    val optional = optionalArguments.map(_.createWeight(searcher, needsScores))
    val terms =
      if (needsScores) {
        val reqSubWeights = required.flatMap(_.subWeights)
        val optSubWeights = optional.flatMap(_.subWeights)
        val subWeights = triggerWeight :: reqSubWeights ::: optSubWeights
        OdinsonQuery.getTermContexts(subWeights.asJava)
      } else {
        null
      }
    new OdinsonEventWeight(searcher, terms, triggerWeight, required, optional)
  }

  class OdinsonEventWeight(
    searcher: IndexSearcher,
    terms: JMap[Term, TermContext],
    triggerWeight: OdinsonWeight,
    requiredWeights: List[ArgumentWeight],
    optionalWeights: List[ArgumentWeight]
  ) extends OdinsonWeight(self, searcher, terms) {

    def extractTerms(terms: JSet[Term]): Unit = {
      triggerWeight.extractTerms(terms)
      for {
        argWeight <- requiredWeights ++ optionalWeights
        weight <- argWeight.subWeights
      } weight.extractTerms(terms)
    }

    def extractTermContexts(contexts: JMap[Term, TermContext]): Unit = {
      triggerWeight.extractTermContexts(contexts)
      for {
        argWeight <- requiredWeights ++ optionalWeights
        weight <- argWeight.subWeights
      } weight.extractTermContexts(contexts)
    }

    def getSpans(
      context: LeafReaderContext,
      requiredPostings: SpanWeight.Postings
    ): OdinsonSpans = {
      // we need the trigger to match
      val triggerSpans = triggerWeight.getSpans(context, requiredPostings)
      if (triggerSpans == null) return null
      // get argument spans
      val requiredSpans = requiredWeights.map(_.getSpans(context, requiredPostings))
      if (requiredSpans.exists(_ == null)) return null
      // Optional arguments that fail should be removed from the list
      val optionalSpans = optionalWeights.flatMap { w =>
        Option(w.getSpans(context, requiredPostings))
      }
      // subSpans is required by ConjunctionSpans
      val subSpans = triggerSpans :: requiredSpans.flatMap(_.subSpans)
      // get graphs
      val graphPerDoc = context.reader.getBinaryDocValues(dependenciesField)
      // get token counts
      val numWordsPerDoc = context.reader.getNumericDocValues(sentenceLengthField)
      // return event spans
      new OdinsonEventSpans(
        subSpans.toArray,
        triggerSpans,
        requiredSpans,
        optionalSpans,
        graphPerDoc,
        numWordsPerDoc
      )
    }

  }

}

class OdinsonEventSpans(
  val subSpans: Array[OdinsonSpans],
  val triggerSpans: OdinsonSpans,
  val requiredSpans: List[ArgumentSpans],
  val optionalSpans: List[ArgumentSpans],
  val graphPerDoc: BinaryDocValues,
  val numWordsPerDoc: NumericDocValues
) extends ConjunctionSpans {

  import Spans._

  private var pq: QueueByPosition = null

  private var topPositionOdinsonMatch: OdinsonMatch = null

  override def odinsonMatch: OdinsonMatch = topPositionOdinsonMatch

  def twoPhaseCurrentDocMatches(): Boolean = {
    oneExhaustedInCurrentDoc = false
    pq = QueueByPosition.mkPositionQueue(matchEvents())
    if (pq.size() > 0) {
      atFirstInCurrentDoc = true
      topPositionOdinsonMatch = null
      true
    } else {
      false
    }
  }

  def nextStartPosition(): Int = {
    atFirstInCurrentDoc = false
    if (pq.size() > 0) {
      topPositionOdinsonMatch = pq.pop()
      matchStart = topPositionOdinsonMatch.start
      matchEnd = topPositionOdinsonMatch.end
    } else {
      topPositionOdinsonMatch = null
      matchStart = NO_MORE_POSITIONS
      matchEnd = NO_MORE_POSITIONS
    }
    matchStart
  }

  // returns the origin of the traversed path (the trigger)
  @tailrec
  private def getStartOfPath(m: OdinsonMatch): OdinsonMatch = {
    m match {
      case m: GraphTraversalMatch => getStartOfPath(m.srcMatch)
      case m                      => m
    }
  }

  // returns a map from trigger to all its captured arguments
  private def matchArgument(
    graph: DirectedGraph,
    maxToken: Int,
    srcMatches: Array[OdinsonMatch],
    argument: ArgumentSpans
  ): Map[OdinsonMatch, Array[(ArgumentSpans, OdinsonMatch)]] = {
    if (srcMatches.isEmpty) return Map.empty
    val matches = argument.fullTraversal.matchFullTraversal(graph, maxToken, srcMatches)
    matches
      .groupBy(getStartOfPath) // the start should be the trigger
      .transform((k, v) => v.map(m => (argument, m)))
  }

  // advance all spans in arg to the specified doc
  private def advanceArgToDoc(arg: ArgumentSpans, doc: Int): Boolean = {
    arg.fullTraversal.advanceToDoc(doc)
  }

  private def matchEvents(): Array[EventSketch] = {
    // at this point, all required arguments seem to match
    // but we need to confirm using the dependency graph
    val graph = UnsafeSerializer.bytesToGraph(graphPerDoc.get(docID()).bytes)
    val maxToken = numWordsPerDoc.get(docID()).toInt
    // get all trigger candidates
    val triggerMatches = triggerSpans.getAllMatches()
    var eventSketches: Map[OdinsonMatch, Array[(ArgumentSpans, OdinsonMatch)]] = Map.empty
    if (requiredSpans.nonEmpty) {
      // we need to advance manually because FullTraversal does some bookkeeping
      requiredSpans.foreach(arg => advanceArgToDoc(arg, docID()))
      // use dependency graph to confirm connection between trigger and required arg
      eventSketches = matchArgument(graph, maxToken, triggerMatches, requiredSpans(0))
      var i = 1
      while (i < requiredSpans.length) {
        val arg = requiredSpans(i)
        i += 1
        val newTriggerCandidates = eventSketches.keys.toArray
        val argMatches = matchArgument(graph, maxToken, newTriggerCandidates, arg)
        val newEventSketches = argMatches.transform { (trigger, matches) =>
          eventSketches(trigger) ++ matches
        }
        eventSketches = newEventSketches
      }
      // if no trigger matches all required args then we're done
      if (eventSketches.isEmpty) return Array.empty
    }
    // either all required args have a valid connection to the trigger,
    // or there are no required args,
    if (eventSketches.isEmpty) {
      // if no required args, then all triggers matches are valid event matches
      var i = 0
      while (i < triggerMatches.length) {
        val t = triggerMatches(i)
        i += 1
        eventSketches = eventSketches.updated(t, Array.empty)
      }
    }
    // try to retrieve all matching optional arguments
    var i = 0
    while (i < optionalSpans.length) {
      val arg = optionalSpans(i)
      i += 1
      if (advanceArgToDoc(arg, docID())) {
        val triggerCandidates = eventSketches.keys.toArray
        val argMatches = matchArgument(graph, maxToken, triggerCandidates, arg)
        val newEventSketches = eventSketches.transform { (trigger, matches) =>
          matches ++ argMatches.getOrElse(trigger, Array.empty)
        }
        eventSketches = newEventSketches
      }
    }
    // then put together the resulting event mentions
    eventSketches
      .map { case (t, args) => new EventSketch(t, args) }
      .toArray
  }

}
