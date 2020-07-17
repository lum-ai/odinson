package ai.lum.odinson.lucene.search

import java.util.{Map => JMap, Set => JSet}

import ai.lum.common.itertools.product
import ai.lum.odinson._
import ai.lum.odinson.digraph._
import ai.lum.odinson.lucene.search.spans._
import ai.lum.odinson.lucene.util._
import ai.lum.odinson.serialization.UnsafeSerializer
import ai.lum.odinson.state.State
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.search.spans._

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable.{ArrayBuffer, ArrayBuilder, HashSet}

case class ArgumentQuery(
  name: String,
  label: Option[String],
  min: Int,
  max: Option[Int],
  fullTraversal: List[(GraphTraversal, OdinsonQuery)]
) {

  def setState(stateOpt: Option[State]): Unit = {
    fullTraversal.foreach { case (_, odinsonQuery) =>
      odinsonQuery.setState(stateOpt)
    }
  }

  def toString(field: String): String = {
    val traversal = fullTraversal
      .map(h => s"(${h._1}, ${h._2.toString(field)})")
      .mkString("(", ", ", ")")
    s"ArgumentQuery($name, $label, $min, $max, $traversal)"
  }

  def createWeight(
    searcher: IndexSearcher,
    needsScores: Boolean
  ): ArgumentWeight = {
    val allWeights = fullTraversal.map { case (g, q) =>
      val w = q.createWeight(searcher, needsScores).asInstanceOf[OdinsonWeight]
      (g, w)
    }
    ArgumentWeight(name, label, min, max, allWeights)
  }

  def rewrite(reader: IndexReader): ArgumentQuery = {
    val rewrittenTraversal = fullTraversal.map { case (g, q) =>
      val r = q.rewrite(reader).asInstanceOf[OdinsonQuery]
      (g, r)
    }
    if (rewrittenTraversal != fullTraversal) {
      ArgumentQuery(name, label, min, max, rewrittenTraversal)
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
  fullTraversal: List[(GraphTraversal, OdinsonWeight)]
) {

  def getSpans(
    context: LeafReaderContext,
    requiredPostings: SpanWeight.Postings
  ): ArgumentSpans = {
    val allSpans = fullTraversal.map { case (g, w) =>
      val s = w.getSpans(context, requiredPostings)
      // if any subspan is null, then the entire argument should fail
      if (s == null) return null
      (g, s)
    }
    ArgumentSpans(name, label, min, max, allSpans)
  }

  def subWeights: List[OdinsonWeight] = {
    fullTraversal.map(_._2)
  }

}

case class ArgumentSpans(
  name: String,
  label: Option[String],
  min: Int,
  max: Option[Int],
  fullTraversal: List[(GraphTraversal, OdinsonSpans)]
) {

  def subSpans: List[OdinsonSpans] = {
    val ss = fullTraversal.map(_._2)
    // if any subspan is null then the whole argument should fail
    if (ss.exists(s => s == null)) null
    else ss
  }

}

class OdinsonEventQuery(
  val trigger: OdinsonQuery,
  val requiredArguments: List[ArgumentQuery],
  val optionalArguments: List[ArgumentQuery],
  val dependenciesField: String,
  val sentenceLengthField: String,
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
    if (trigger != rewrittenTrigger || requiredArguments != rewrittenRequiredArguments || optionalArguments != rewrittenOptionalArguments) {
      new OdinsonEventQuery(
        rewrittenTrigger,
        rewrittenRequiredArguments,
        rewrittenOptionalArguments,
        dependenciesField,
        sentenceLengthField,
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
      if (requiredSpans.exists(_ == null)) return null
      // Optional arguments that fail should be removed from the list
      val optionalSpans = optionalWeights.flatMap{ w =>
        Option(w.getSpans(context, requiredPostings))
      }
      // subSpans is required by ConjunctionSpans
      val subSpans = triggerSpans :: requiredSpans.flatMap(_.subSpans)
      // get graphs
      val graphPerDoc = context.reader.getSortedDocValues(dependenciesField)
      // get token counts
      val numWordsPerDoc = context.reader.getNumericDocValues(sentenceLengthField)
      // return event spans
      new OdinsonEventSpans(
        subSpans.toArray,
        triggerSpans,
        requiredSpans,
        optionalSpans,
        graphPerDoc,
        numWordsPerDoc,
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
  val numWordsPerDoc: NumericDocValues,
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

  // returns a map from token index to all matches that contain that token
  private def mkInvIndex(spans: Array[OdinsonMatch], maxToken: Int): Array[ArrayBuffer[OdinsonMatch]] = {
    val index = new Array[ArrayBuffer[OdinsonMatch]](maxToken)
    // empty buffer meant to stay empty and be reused
    val empty = new ArrayBuffer[OdinsonMatch]
    // add mentions at the corresponding token positions
    var i = 0
    while (i < spans.length) {
      val s = spans(i)
      i += 1
      var j = s.start
      while (j < s.end) {
        if (index(j) == null) {
          // make a new buffer at this position
          index(j) = new ArrayBuffer[OdinsonMatch]
        }
        index(j) += s
        j += 1
      }
    }
    // add the empty buffer everywhere else
    i = 0
    while (i < index.length) {
      if (index(i) == null) {
        index(i) = empty
      }
      i += 1
    }
    index
  }

  // performs one step in the full traversal
  private def matchPairs(
    graph: DirectedGraph,
    maxToken: Int,
    traversal: GraphTraversal,
    srcMatches: Array[OdinsonMatch],
    dstMatches: Array[OdinsonMatch]
  ): Array[OdinsonMatch] = {
    val builder = new ArrayBuilder.ofRef[OdinsonMatch]
    val dstIndex = mkInvIndex(dstMatches, maxToken)
    for {
      src <- srcMatches
      seen = HashSet.empty[OdinsonMatch]
      path <- traversal.traverseFrom(graph, src)
      dst <- dstIndex(path.end)
      if seen.add(dst)
    } builder += new GraphTraversalMatch(src, dst, path)
    builder.result()
  }

  // performs the full traversal from trigger to argument
  private def matchFullTraversal(
    graph: DirectedGraph,
    maxToken: Int,
    srcMatches: Array[OdinsonMatch],
    fullTraversal: Seq[(GraphTraversal, OdinsonSpans)]
  ): Array[OdinsonMatch] = {
    var currentSpans = srcMatches
    for ((traversal, spans) <- fullTraversal) {
      val dstMatches = spans.getAllMatches()
      currentSpans = matchPairs(graph, maxToken, traversal, currentSpans, dstMatches)
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
    maxToken: Int,
    srcMatches: Array[OdinsonMatch],
    argument: ArgumentSpans
  ): Map[OdinsonMatch, Array[(ArgumentSpans, OdinsonMatch)]] = {
    if (srcMatches.isEmpty) return Map.empty
    val matches = matchFullTraversal(graph, maxToken, srcMatches, argument.fullTraversal)
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
      case ArgumentSpans(name, None, min, Some(max), _) if min == max =>
        // exact range (note that a range 1-1 means no quantifier)
        matches.combinations(min).toList
      case ArgumentSpans(name, None, min, Some(max), _) =>
        // range with min and max (note that min could be 0)
        if (matches.size < min) Nil
        else if (matches.size > max) matches.combinations(max).toList
        else Seq(matches)
      case ArgumentSpans(name, None, min, None, _) =>
        // at least min
        if (matches.size < min) Nil
        else Seq(matches)
    }
    for (pkg <- packages) yield {
      pkg.map(m => NamedCapture(arg.name, arg.label, m))
    }
  }

  private def packageArguments(
    args: Array[(ArgumentSpans, OdinsonMatch)]
  ): Array[Array[NamedCapture]] = {
    val packaged = args.groupBy(_._1).map { case (arg, values) =>
      // values is a sequence of (arg, match) tuples, so discard the arg
      val matches = values.map(_._2)
      packageArgument(arg, matches)
    }
    // return cartesian product of arguments
    product(packaged.toSeq).map(_.flatten.toArray).toArray
  }

  // get an event sketch and return a sequence of EventMatch objects
  private def packageEvents(
    sketch: (OdinsonMatch, Array[(ArgumentSpans, OdinsonMatch)])
  ): Array[EventMatch] = {
    val trigger = sketch._1
    val argumentPackages = packageArguments(sketch._2)
    argumentPackages.map(args => new EventMatch(trigger, args, args.map { it => ArgumentMetadata(it.name, it.capturedMatch.start, Some(it.capturedMatch.end)) }))
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
