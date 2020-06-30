package ai.lum.odinson.serialization

import org.scalatest._
import ai.lum.odinson.digraph.DirectedGraph

import ai.lum.odinson.BaseSpec

class TestUnsafeSerializer extends BaseSpec {
  // TODO use a real dependency graph
  val g = new DirectedGraph(
    Array(1,2,3,4,5,6,7,8,9,12,12,12,12,23,4,6,7,8,3,5,6,78,8,9,4,5,1,2,3,4,3,4),
    Array(0,14,26,26,30,30,30,32),
    Array(1,2,3,4,5,6,7,8,9,10,1,2,1,2,3,4,2,3,4,5,6,7,1,2,3,4,5,6,7,8),
    Array(0,10,10,12,16,16,16,30),
    Array(1,2),
  )
  //
  "UnsafeSerializer" should "serialize and deserialize a DirectedGraph" in {
    val bytes = UnsafeSerializer.graphToBytes(g)
    val graph = UnsafeSerializer.bytesToGraph(bytes)
    graph.incomingFlat should be (g.incomingFlat)
    graph.incomingSlices should be (g.incomingSlices)
    graph.outgoingFlat should be (g.outgoingFlat)
    graph.outgoingSlices should be (g.outgoingSlices)
    graph.roots should be (g.roots)
  }
}
