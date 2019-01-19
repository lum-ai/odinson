package ai.lum.odinson.digraph

import ai.lum.odinson.kryo.OdinKryoPool

// incoming: array whose elements correspond to the incoming edges of each token
//   and the edges are represented as an array where (node, label) pairs have been flattened
//   and label is represented as an id from a vocabulary
// outgoing: ditto
// roots: root nodes
case class DirectedGraph(
    incoming: Array[Array[Int]],
    outgoing: Array[Array[Int]],
    roots: Array[Int]
) {

  def toBytes: Array[Byte] = DirectedGraph.kryoPool.toBytesWithClass(this)

}

object DirectedGraph {

  private val poolSize = 1 // TODO pull from config

  private val kryoPool = new OdinKryoPool(poolSize)

  def fromBytes(bytes: Array[Byte]): DirectedGraph = {
    kryoPool.fromBytes(bytes).asInstanceOf[DirectedGraph]
  }

}
