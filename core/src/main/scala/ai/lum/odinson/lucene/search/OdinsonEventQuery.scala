package ai.lum.odinson.lucene.search

import java.util.{ Map => JMap, Set => JSet }
import scala.annotation.tailrec
import scala.collection.mutable.{ ArrayBuffer, HashMap }
import scala.collection.JavaConverters._
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._
import ai.lum.odinson._
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.util._
import ai.lum.odinson.lucene.search.spans._
import ai.lum.odinson.digraph._
import ai.lum.common.itertools.product

case class ArgumentQuery(
  name: String,
  min: Int,
  max: Option[Int],
  fullTraversal: List[(GraphTraversal, OdinsonQuery)]
) {

  def toString(field: String): String = {
    val traversal = fullTraversal
      .map(h => s"(${h._1}, ${h._2.toString(field)})")
      .mkString("(", ", ", ")")
    s"ArgumentQuery($name, $min, $max, $traversal)"
  }

  def createWeight(
    searcher: IndexSearcher,
    needsScores: Boolean
  ): ArgumentWeight = {
    val allWeights = fullTraversal.map { case (g, q) =>
      val w = q.createWeight(searcher, needsScores).asInstanceOf[OdinsonWeight]
      (g, w)
    }
    ArgumentWeight(name, min, max, allWeights)
  }

  def rewrite(reader: IndexReader): ArgumentQuery = {
    val rewrittenTraversal = fullTraversal.map { case (g, q) =>
      val r = q.rewrite(reader).asInstanceOf[OdinsonQuery]
      (g, r)
    }
    if (rewrittenTraversal != fullTraversal) {
      ArgumentQuery(name, min, max, rewrittenTraversal)
    } else {
      this
    }
  }

}

case class ArgumentWeight(
  name: String,
  min: Int,
  max: Option[Int],
  fullTraversal: List[(GraphTraversal, OdinsonWeight)]
) {

  def getSpans(
    context: LeafReaderContext,
    requiredPostings: SpanWeight.Postings
  ): ArgumentSpans = {
    val allSpans = fullTraversal.map { case (g, w) =>
      val s = w.getSpans(context, requiredPostings)
      (g, s)
    }
    ArgumentSpans(name, min, max, allSpans)
  }

  def subWeights: List[OdinsonWeight] = {
    fullTraversal.map(_._2)
  }

}

case class ArgumentSpans(
  name: String,
  min: Int,
  max: Option[Int],
  fullTraversal: List[(GraphTraversal, OdinsonSpans)]
) {

  def subSpans: List[OdinsonSpans] = {
    fullTraversal.map(_._2)
  }

}

class OdinsonEventQuery(
  val trigger: OdinsonQuery,
  val requiredArguments: List[ArgumentQuery],
  val optionalArguments: List[ArgumentQuery],
  val dependenciesField: String,
) extends OdinsonQuery { self =>

  override def hashCode: Int = mkHash(trigger, requiredArguments, optionalArguments, dependenciesField)

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
    if (trigger != rewrittenTrigger || requiredArguments != rewrittenRequiredArguments || optionalArguments != rewrittenOptionalArguments) {
      new OdinsonEventQuery(
        rewrittenTrigger,
        rewrittenRequiredArguments,
        rewrittenOptionalArguments,
        dependenciesField
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
    optionalWeights: List[ArgumentWeight],
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
      if (requiredSpans.exists(_.subSpans == null)) return null
      val optionalSpans = optionalWeights.map(_.getSpans(context, requiredPostings))
      // subSpans is required by ConjunctionSpans
      val subSpans = triggerSpans :: requiredSpans.flatMap(_.subSpans)
      // get graphs
      val graphPerDoc = context.reader.getSortedDocValues(dependenciesField)
      // return event spans
      new OdinsonEventSpans(
        subSpans.toArray,
        triggerSpans,
        requiredSpans,
        optionalSpans,
        graphPerDoc,
      )
    }

  }

}

class OdinsonEventSpans(
  val subSpans: Array[OdinsonSpans],
  val triggerSpans: OdinsonSpans,
  val requiredSpans: List[ArgumentSpans],
  val optionalSpans: List[ArgumentSpans],
  val graphPerDoc: SortedDocValues,
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

  // gets all OdinsonMatches from an OdinsonSpans iterator for the current doc
  private def getAllMatches(spans: OdinsonSpans): Array[OdinsonMatch] = {
    val buffer = ArrayBuffer.empty[OdinsonMatch]
    while (spans.nextStartPosition() != NO_MORE_POSITIONS) {
      buffer += spans.odinsonMatch
    }
    buffer.toArray
  }

  // returns a map from token index to all matches that contain that token
  private def mkInvertedIndex(
    matches: Seq[OdinsonMatch]
  ): Map[Int, ArrayBuffer[OdinsonMatch]] = {
    val index = HashMap.empty[Int, ArrayBuffer[OdinsonMatch]]
    for {
      m <- matches
      i <- m.tokenInterval
    } index.getOrElseUpdate(i, ArrayBuffer.empty) += m
    index.toMap.withDefaultValue(ArrayBuffer.empty)
  }

  // performs one step in the full traversal
  private def matchPairs(
    graph: DirectedGraph,
    traversal: GraphTraversal,
    srcMatches: Array[OdinsonMatch],
    dstMatches: Array[OdinsonMatch]
  ): Array[OdinsonMatch] = {
    val results = ArrayBuffer.empty[OdinsonMatch]
    val dstIndex = mkInvertedIndex(dstMatches)
    for (src <- srcMatches) {
      val dsts = traversal.traverseFrom(graph, src.tokenInterval)
      results ++= dsts
        .flatMap(dstIndex)
        .distinct
        .map(dst => new GraphTraversalMatch(src, dst))
    }
    results.toArray
  }

  // performs the full traversal from trigger to argument
  private def matchFullTraversal(
    graph: DirectedGraph,
    srcMatches: Array[OdinsonMatch],
    fullTraversal: Seq[(GraphTraversal, OdinsonSpans)]
  ): Array[OdinsonMatch] = {
    var currentSpans = srcMatches
    for ((traversal, spans) <- fullTraversal) {
      val dstMatches = getAllMatches(spans)
      currentSpans = matchPairs(graph, traversal, currentSpans, dstMatches)
      if (currentSpans.isEmpty) return Array.empty
    }
    currentSpans
  }

  // returns the origin of the traversed path (the trigger)
  @tailrec
  private def getStartOfPath(m: OdinsonMatch): OdinsonMatch = {
    m match {
      case m: GraphTraversalMatch => getStartOfPath(m.srcMatch)
      case m => m
    }
  }

  // returns a map from trigger to all its captured arguments
  private def matchArgument(
    graph: DirectedGraph,
    srcMatches: Array[OdinsonMatch],
    argument: ArgumentSpans
  ): Map[OdinsonMatch, Array[(ArgumentSpans, OdinsonMatch)]] = {
    if (srcMatches.isEmpty) return Map.empty
    val matches = matchFullTraversal(graph, srcMatches, argument.fullTraversal)
    matches
      .groupBy(getStartOfPath) // the start should be the trigger
      .transform((k,v) => v.map(m => (argument, m)))
  }

  // advance all spans in arg to the specified doc
  private def advanceArgToDoc(arg: ArgumentSpans, doc: Int): Boolean = {
    // try to advance all spans in fullTraversal to the current doc
    for ((traversal, spans) <- arg.fullTraversal) {
      // if the spans haven't advanced then try to catch up
      if (spans.docID() < doc) {
        spans.advance(doc)
      }
      // if spans are beyond current doc then this arg doesn't match current doc
      if (spans.docID() > doc) { // FIXME no_more_docs?
        return false
      }
    }
    // every spans is at current doc
    true
  }

  private def packageArgument(
    arg: ArgumentSpans,
    matches: Seq[OdinsonMatch]
  ): Seq[Seq[NamedCapture]] = {
    val packages = arg match {
      case ArgumentSpans(name, min, Some(max), _) if min == max =>
        // exact range (note that a range 1-1 means no quantifier)
        matches.combinations(min).toList
      case ArgumentSpans(name, min, Some(max), _) =>
        // range with min and max (note that min could be 0)
        if (matches.size < min) Nil
        else if (matches.size > max) matches.combinations(max).toList
        else Seq(matches)
      case ArgumentSpans(name, min, None, _) =>
        // at least min
        if (matches.size < min) Nil
        else Seq(matches)
    }
    for (pkg <- packages) yield {
      pkg.map(m => NamedCapture(arg.name, m))
    }
  }

  private def packageArguments(
    args: Array[(ArgumentSpans, OdinsonMatch)]
  ): Seq[Seq[NamedCapture]] = {
    val packaged = args.groupBy(_._1).map { case (arg, values) =>
      // values is a sequence of (arg, match) tuples, so discard the arg
      val matches = values.map(_._2)
      packageArgument(arg, matches)
    }
    // return cartesian product of arguments
    product(packaged.toList).map(_.flatten)
  }

  // get an event sketch and return a sequence of EventMatch objects
  private def packageEvents(
    sketch: (OdinsonMatch, Array[(ArgumentSpans, OdinsonMatch)])
  ): Array[EventMatch] = {
    val trigger = sketch._1
    val argumentPackages = packageArguments(sketch._2)
    argumentPackages.map(args => new EventMatch(trigger, args.toList)).toArray
  }

  private def matchEvents(): Array[EventMatch] = {
    // at this point, all required arguments seem to match
    // but we need to confirm using the dependency graph
    val graph = DirectedGraph.fromBytes(graphPerDoc.get(docID()).bytes)
    // get all trigger candidates
    val triggerMatches = getAllMatches(triggerSpans)
    var eventSketches: Map[OdinsonMatch, Array[(ArgumentSpans, OdinsonMatch)]] = Map.empty
    if (requiredSpans.nonEmpty) {
      // use dependency graph to confirm connection between trigger and required arg
      eventSketches = matchArgument(graph, triggerMatches, requiredSpans.head)
      for (arg <- requiredSpans.tail) {
        val newTriggerCandidates = eventSketches.keys.toArray
        val argMatches = matchArgument(graph, newTriggerCandidates, arg)
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
      for (t <- triggerMatches) {
        eventSketches = eventSketches.updated(t, Array.empty)
      }
    }
    // try to retrieve all matching optional arguments
    for (arg <- optionalSpans) {
      if (advanceArgToDoc(arg, docID())) {
        val triggerCandidates = eventSketches.keys.toArray
        val argMatches = matchArgument(graph, triggerCandidates, arg)
        val newEventSketches = eventSketches.transform { (trigger, matches) =>
          matches ++ argMatches.getOrElse(trigger, Array.empty)
        }
        eventSketches = newEventSketches
      }
    }
    // then put together the resulting event mentions
    eventSketches.flatMap(packageEvents).toArray
  }

}
