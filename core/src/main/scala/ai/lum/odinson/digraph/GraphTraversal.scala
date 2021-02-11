package ai.lum.odinson.digraph

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuilder
import ai.lum.odinson.OdinsonMatch

/** A GraphTraversal takes a graph and a starting point (or several)
  * and returns the ending points of the traversal.
  */
trait GraphTraversal {
  protected def traverse(graph: DirectedGraph, startNode: Int): Seq[Int]

  // the results of traverseFrom() should be distinct
  def traverseFrom(graph: DirectedGraph, startNode: Int): Seq[Int] =
    traverse(graph, startNode).distinct

  def traverseFrom(graph: DirectedGraph, startNodes: Seq[Int]): Seq[Int] =
    startNodes.flatMap(n => traverse(graph, n)).distinct

  def traverseFrom(graph: DirectedGraph, odinsonMatch: OdinsonMatch): Seq[Int] =
    traverseFrom(graph, odinsonMatch.tokenInterval)

}

/** a no-op traversal */
case object NoTraversal extends GraphTraversal {
  def traverse(graph: DirectedGraph, startNode: Int): Seq[Int] = Seq(startNode)

  override def traverseFrom(graph: DirectedGraph, startNode: Int): Seq[Int] =
    Seq(startNode)

  override def traverseFrom(
    graph: DirectedGraph,
    startNodes: Seq[Int]
  ): Seq[Int] = startNodes.distinct

  override def traverseFrom(
    graph: DirectedGraph,
    odinsonMatch: OdinsonMatch
  ): Seq[Int] = odinsonMatch.tokenInterval

}

/** a traversal that always fails */
case object FailTraversal extends GraphTraversal {
  def traverse(graph: DirectedGraph, startNode: Int): Seq[Int] = Nil

  override def traverseFrom(graph: DirectedGraph, startNode: Int): Seq[Int] =
    Nil

  override def traverseFrom(
    graph: DirectedGraph,
    startNodes: Seq[Int]
  ): Seq[Int] = Nil

  override def traverseFrom(
    graph: DirectedGraph,
    odinsonMatch: OdinsonMatch
  ): Seq[Int] = Nil

}

/** traverse all incoming edges */
case object IncomingWildcard extends GraphTraversal {

  def traverse(graph: DirectedGraph, startNode: Int): Seq[Int] = {
    if (startNode < graph.incomingSlices.length - 1) {
      val start = graph.incomingSlices(startNode)
      val stop = graph.incomingSlices(startNode + 1)
      val results = new Array[Int]((stop - start) / 2)
      var i = start
      var j = 0
      while (i < stop) {
        results(j) = graph.incomingFlat(i)
        i += 2
        j += 1
      }
      results
    } else {
      Array.emptyIntArray
    }
  }

}

/** traverse all outgoing edges */
case object OutgoingWildcard extends GraphTraversal {

  def traverse(graph: DirectedGraph, startNode: Int): Seq[Int] = {
    if (startNode < graph.outgoingSlices.length - 1) {
      val start = graph.outgoingSlices(startNode)
      val stop = graph.outgoingSlices(startNode + 1)
      val results = new Array[Int]((stop - start) / 2)
      var i = start
      var j = 0
      while (i < stop) {
        results(j) = graph.outgoingFlat(i)
        i += 2
        j += 1
      }
      results
    } else {
      Array.emptyIntArray
    }
  }

}

/** traverse an incoming edge whose label can be matched by the label matcher */
case class Incoming(matcher: LabelMatcher) extends GraphTraversal {

  def traverse(graph: DirectedGraph, startNode: Int): Seq[Int] = {
    if (startNode < graph.incomingSlices.length - 1) {
      val start = graph.incomingSlices(startNode)
      val stop = graph.incomingSlices(startNode + 1)
      val builder = new ArrayBuilder.ofInt
      builder.sizeHint((stop - start) / 2)
      var i = start
      while (i < stop) {
        if (matcher matches graph.incomingFlat(i + 1)) {
          builder += graph.incomingFlat(i)
        }
        i += 2
      }
      builder.result()
    } else {
      Array.emptyIntArray
    }
  }

}

/** traverse an outgoing edge whose label can be matched by the label matcher */
case class Outgoing(matcher: LabelMatcher) extends GraphTraversal {

  def traverse(graph: DirectedGraph, startNode: Int): Seq[Int] = {
    if (startNode < graph.outgoingSlices.length - 1) {
      val start = graph.outgoingSlices(startNode)
      val stop = graph.outgoingSlices(startNode + 1)
      val builder = new ArrayBuilder.ofInt
      builder.sizeHint((stop - start) / 2)
      var i = start
      while (i < stop) {
        if (matcher matches graph.outgoingFlat(i + 1)) {
          builder += graph.outgoingFlat(i)
        }
        i += 2
      }
      builder.result()
    } else {
      Array.emptyIntArray
    }
  }

}

/** execute a series of traversals where each one starts at the result of the previous one */
case class Concatenation(traversals: List[GraphTraversal])
    extends GraphTraversal {

  def traverse(graph: DirectedGraph, startNode: Int): Seq[Int] = {
    if (traversals.isEmpty) return Nil
    traversals.foldLeft(Seq(startNode)) {
      case (currentNodes, traversal) =>
        if (currentNodes.isEmpty) return Nil
        traversal.traverseFrom(graph, currentNodes)
    }
  }

}

/** the union of the results of several traversals */
case class Union(traversals: List[GraphTraversal]) extends GraphTraversal {

  def traverse(graph: DirectedGraph, startNode: Int): Seq[Int] = {
    traversals.flatMap(_.traverseFrom(graph, startNode))
  }

}

/** a traversal that is optional */
case class Optional(traversal: GraphTraversal) extends GraphTraversal {

  def traverse(graph: DirectedGraph, startNode: Int): Seq[Int] = {
    startNode +: traversal.traverseFrom(graph, startNode)
  }

}

/** a traversal that matches zero or more times. */
case class KleeneStar(traversal: GraphTraversal) extends GraphTraversal {

  def traverse(graph: DirectedGraph, startNode: Int): Seq[Int] = {
    collect(graph, Seq(startNode), Set.empty)
  }

  override def traverseFrom(
    graph: DirectedGraph,
    startNodes: Seq[Int]
  ): Seq[Int] = {
    collect(graph, startNodes, Set.empty)
  }

  @tailrec
  private def collect(
    graph: DirectedGraph,
    remaining: Seq[Int],
    seen: Set[Int]
  ): Seq[Int] = remaining match {
    case Seq()                              => seen.toSeq
    case node +: rest if seen contains node => collect(graph, rest, seen)
    case node +: rest =>
      collect(graph, traversal.traverseFrom(graph, node) ++ rest, seen + node)
  }

}
