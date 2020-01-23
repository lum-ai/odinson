package ai.lum.odinson.digraph

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuilder
import ai.lum.odinson.OdinsonMatch
import ai.lum.odinson.utils.{ ArrayUtils, DistinctBy }

/**
 * A GraphTraversal takes a graph and a starting point (or several)
 * and returns the ending points of the traversal.
 */
trait GraphTraversal {

  protected def traverse(graph: DirectedGraph, traversedPath: TraversedPath): Array[TraversedPath]

  protected def traverse(graph: DirectedGraph, startNode: Int): Array[TraversedPath] = {
    traverse(graph, new TraversedPath(startNode))
  }

  def traverseFrom(graph: DirectedGraph, startNode: Int): Array[TraversedPath] = {
    val traversals = traverse(graph, startNode)
    val distinctTraversals = new DistinctBy[TraversedPath, Int](traversals, _.end)
    distinctTraversals.toArray
  }

  def traverseFrom(graph: DirectedGraph, startNodes: Seq[Int]): Array[TraversedPath] = {
    val traversals = startNodes.flatMap(n => traverse(graph, n))
    val distinctTraversals = new DistinctBy[TraversedPath, Int](traversals, _.end)
    distinctTraversals.toArray
  }

  def traverseFrom(graph: DirectedGraph, odinsonMatch: OdinsonMatch): Array[TraversedPath] = {
    traverseFrom(graph, odinsonMatch.tokenInterval)
  }

  def traverseFrom(graph: DirectedGraph, traversedPath: TraversedPath): Array[TraversedPath] = {
    val traversals = traverse(graph, traversedPath)
    val distinctTraversals = new DistinctBy[TraversedPath, Int](traversals, _.end)
    distinctTraversals.toArray
  }

  def traverseFrom(graph: DirectedGraph, traversedPaths: Array[TraversedPath]): Array[TraversedPath] = {
    val traversals = traversedPaths.flatMap(p => traverse(graph, p))
    val distinctTraversals = new DistinctBy[TraversedPath, Int](traversals, _.end)
    distinctTraversals.toArray
  }

}

object GraphTraversal {

  val emptyTraversal = new Array[TraversedPath](0)

}

/** a no-op traversal */
case object NoTraversal extends GraphTraversal {
  def traverse(graph: DirectedGraph, traversedPath: TraversedPath): Array[TraversedPath] = Array(traversedPath)
  override def traverseFrom(graph: DirectedGraph, traversedPath: TraversedPath): Array[TraversedPath] = Array(traversedPath)
  override def traverseFrom(graph: DirectedGraph, traversedPaths: Array[TraversedPath]): Array[TraversedPath] = traversedPaths
}

/** a traversal that always fails */
case object FailTraversal extends GraphTraversal {
  def traverse(graph: DirectedGraph, traversedPath: TraversedPath): Array[TraversedPath] = GraphTraversal.emptyTraversal
  override def traverse(graph: DirectedGraph, startNode: Int): Array[TraversedPath] = GraphTraversal.emptyTraversal
  override def traverseFrom(graph: DirectedGraph, startNode: Int): Array[TraversedPath] = GraphTraversal.emptyTraversal
  override def traverseFrom(graph: DirectedGraph, startNodes: Seq[Int]): Array[TraversedPath] = GraphTraversal.emptyTraversal
  override def traverseFrom(graph: DirectedGraph, odinsonMatch: OdinsonMatch): Array[TraversedPath] = GraphTraversal.emptyTraversal
  override def traverseFrom(graph: DirectedGraph, traversedPath: TraversedPath): Array[TraversedPath] = GraphTraversal.emptyTraversal
  override def traverseFrom(graph: DirectedGraph, traversedPaths: Array[TraversedPath]): Array[TraversedPath] = GraphTraversal.emptyTraversal
}

/** traverse all incoming edges */
case object IncomingWildcard extends GraphTraversal {
  def traverse(graph: DirectedGraph, traversedPath: TraversedPath): Array[TraversedPath] = {
    val startNode = traversedPath.end
    if (startNode < graph.incomingSlices.length - 1) {
      // find the range of values we need to check in graph.incomingFlat
      val start = graph.incomingSlices(startNode)
      val stop = graph.incomingSlices(startNode + 1)
      // allocate results array
      // recall that incomingFlat encodes (node, label) tuples flattened
      // so we need to divide by two to get the total number of incoming edges
      val results = new Array[TraversedPath]((stop - start) / 2)
      // start iterating
      var i = start // index to incomingFlat
      var j = 0     // index to results
      while (i < stop) {
        // get edge info
        val token = graph.incomingFlat(i)
        val edgeLabel = graph.incomingFlat(i + 1)
        // copy traversed path and add new step
        val traversedCopy = traversedPath.copy()
        val step = TraversedStep(from = startNode, to = token, edgeLabel = edgeLabel, direction = "incoming")
        traversedCopy.addStep(step)
        // add new traversed path to results
        results(j) = traversedCopy
        // go to next incoming edge
        i += 2
        j += 1
      }
      // return results
      results
    } else {
      GraphTraversal.emptyTraversal
    }
  }
}


/** traverse all outgoing edges */
case object OutgoingWildcard extends GraphTraversal {
  def traverse(graph: DirectedGraph, traversedPath: TraversedPath): Array[TraversedPath] = {
    // see the comments in IncomingWildcard
    // this follows the same logic, except that it follows outgoing edges instead of incoming edges
    val startNode = traversedPath.end
    if (startNode < graph.outgoingSlices.length - 1) {
      val start = graph.outgoingSlices(startNode)
      val stop = graph.outgoingSlices(startNode + 1)
      val results = new Array[TraversedPath]((stop - start) / 2)
      var i = start
      var j = 0
      while (i < stop) {
        val token = graph.outgoingFlat(i)
        val edgeLabel = graph.outgoingFlat(i + 1)
        val traversedCopy = traversedPath.copy()
        val step = TraversedStep(from = startNode, to = token, edgeLabel = edgeLabel, direction = "outgoing")
        traversedCopy.addStep(step)
        results(j) = traversedCopy
        i += 2
        j += 1
      }
      results
    } else {
      GraphTraversal.emptyTraversal
    }
  }
}

/** traverse an incoming edge whose label can be matched by the label matcher */
case class Incoming(matcher: LabelMatcher) extends GraphTraversal {
  def traverse(graph: DirectedGraph, traversedPath: TraversedPath): Array[TraversedPath] = {
    val startNode = traversedPath.end
    if (startNode < graph.incomingSlices.length - 1) {
      // find the range of values we need to check in graph.incomingFlat
      val start = graph.incomingSlices(startNode)
      val stop = graph.incomingSlices(startNode + 1)
      // make builder for results array
      val builder = new ArrayBuilder.ofRef[TraversedPath]
      // recall that incomingFlat encodes (node, label) tuples flattened
      // so we need to divide by two to get the total number of incoming edges
      builder.sizeHint((stop - start) / 2)
      // start iterating
      var i = start
      while (i < stop) {
        // get edge info
        val token = graph.incomingFlat(i)
        val edgeLabel = graph.incomingFlat(i + 1)
        if (matcher matches edgeLabel) {
          // copy traversed path and add a new step
          val traversedCopy = traversedPath.copy()
          val step = TraversedStep(from = startNode, to = token, edgeLabel = edgeLabel, direction = "incoming")
          traversedCopy.addStep(step)
          // add new traversed path to results
          builder += traversedCopy
        }
        // go to next incoming edge
        i += 2
      }
      // return results
      builder.result()
    } else {
      GraphTraversal.emptyTraversal
    }
  }
}

/** traverse an outgoing edge whose label can be matched by the label matcher */
case class Outgoing(matcher: LabelMatcher) extends GraphTraversal {
  def traverse(graph: DirectedGraph, traversedPath: TraversedPath): Array[TraversedPath] = {
    // see the comments in Incoming
    // this follows the same logic, except that it follows outgoing edges instead of incoming edges
    val startNode = traversedPath.end
    if (startNode < graph.outgoingSlices.length - 1) {
      val start = graph.outgoingSlices(startNode)
      val stop = graph.outgoingSlices(startNode + 1)
      val builder = new ArrayBuilder.ofRef[TraversedPath]
      builder.sizeHint((stop - start) / 2)
      var i = start
      while (i < stop) {
        val token = graph.outgoingFlat(i)
        val edgeLabel = graph.outgoingFlat(i + 1)
        if (matcher matches edgeLabel) {
          val traversedCopy = traversedPath.copy()
          val step = TraversedStep(from = startNode, to = token, edgeLabel = edgeLabel, direction = "outgoing")
          traversedCopy.addStep(step)
          builder += traversedCopy
        }
        i += 2
      }
      builder.result()
    } else {
      GraphTraversal.emptyTraversal
    }
  }
}

/** execute a series of traversals where each one starts at the result of the previous one */
case class Concatenation(traversals: List[GraphTraversal]) extends GraphTraversal {
  def traverse(graph: DirectedGraph, traversedPath: TraversedPath): Array[TraversedPath] = {
    if (traversals.isEmpty) return GraphTraversal.emptyTraversal
    traversals.foldLeft(Array(traversedPath)) {
      case (currentPaths, traversal) =>
        if (currentPaths.isEmpty) return GraphTraversal.emptyTraversal
        traversal.traverseFrom(graph, currentPaths)
    }
  }
}

/** the union of the results of several traversals */
case class Union(traversals: List[GraphTraversal]) extends GraphTraversal {
  def traverse(graph: DirectedGraph, traversedPath: TraversedPath): Array[TraversedPath] = {
    traversals.flatMap(_.traverseFrom(graph, traversedPath)).toArray
  }
}

/** a traversal that is optional */
case class Optional(traversal: GraphTraversal) extends GraphTraversal {
  def traverse(graph: DirectedGraph, traversedPath: TraversedPath): Array[TraversedPath] = {
    val results = traversal.traverseFrom(graph, traversedPath)
    ArrayUtils.prepend(results, traversedPath)
  }
}

/** a traversal that matches zero or more times. */
case class KleeneStar(traversal: GraphTraversal) extends GraphTraversal {

  def traverse(graph: DirectedGraph, traversedPath: TraversedPath): Array[TraversedPath] = {
    collect(graph, Array(traversedPath), Array.empty, Set.empty)
  }

  @tailrec
  private def collect(graph: DirectedGraph, alivePaths: Array[TraversedPath], results: Array[TraversedPath], seen: Set[Int]): Array[TraversedPath] = {
    alivePaths match {
      case Array() =>
        // we're done
        results
      case Array(path, rest @ _*) if seen contains path.end =>
        // ignore current path and keep going
        collect(graph, rest.toArray, results, seen)
      case Array(path, rest @ _*) =>
        // explore end of current path
        val nextPaths = traversal.traverseFrom(graph, path)
        // put nextPaths after rest to perform a breadth first traversal
        // this should result in shorter paths when several are available
        val nextAlivePaths = ArrayUtils.merge(rest.toArray, nextPaths)
        // the current path is now part of the results
        val nextResults = ArrayUtils.append(results, path)
        // the end of the current path has been already explored (see nextPaths above)
        // we should not explore it again
        val nextSeen = seen + path.end
        // keep going
        collect(graph, nextAlivePaths, nextResults, nextSeen)
    }
  }

}
