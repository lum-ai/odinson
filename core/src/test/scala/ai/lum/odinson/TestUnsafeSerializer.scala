package ai.lum.odinson

import org.scalatest._
import ai.lum.odinson.digraph.DirectedGraph
import ai.lum.odinson.serialization.UnsafeSerializer

class TestUnsafeSerializer extends FlatSpec with Matchers {

  // TODO use a real dependency graph
  val g = DirectedGraph(
    Array(
      Array(1,2,3,4,5,6,7,8,9,12,12,12,12,23),
      Array(4,6,7,8,3,5,6,78,8,9,4,5),
      Array(),
      Array(1,2,3,4),
      Array(),
      Array(),
      Array(3,4),
    ),
    Array(
      Array(1,2,3,4,5,6,7,8,9,10),
      Array(),
      Array(1,2),
      Array(1,2,3,4),
      Array(),
      Array(),
      Array(2,3,4,5,6,7,1,2,3,4,5,6,7,8),
    ),
    Array(1,2),
  )

  val f = new UnsafeSerializer.FlatGraph(
    Array(1,2,3,4,5,6,7,8,9,12,12,12,12,23,4,6,7,8,3,5,6,78,8,9,4,5,1,2,3,4,3,4),
    Array(0,14,14,26,26,26,26,30,30,30,30,30,30,32),
    Array(1,2,3,4,5,6,7,8,9,10,1,2,1,2,3,4,2,3,4,5,6,7,1,2,3,4,5,6,7,8),
    Array(0,10,10,10,10,12,12,16,16,16,16,16,16,30),
    Array(1,2),
  )

  "UnsafeSerializer" should "flatten a DirectedGraph" in {
    val flat = UnsafeSerializer.flattenGraph(g)
    flat.incomingFlat should be (f.incomingFlat)
    flat.incomingSlices should be (f.incomingSlices)
    flat.outgoingFlat should be (f.outgoingFlat)
    flat.outgoingSlices should be (f.outgoingSlices)
    flat.roots should be (f.roots)
  }

  it should "serialize and deserialize a DirectedGraph" in {
    val bytes = UnsafeSerializer.graphToBytes(g)
    val graph = UnsafeSerializer.bytesToGraph(bytes)
    graph.incoming should be (g.incoming)
    graph.outgoing should be (g.outgoing)
    graph.roots should be (g.roots)
  }

}
