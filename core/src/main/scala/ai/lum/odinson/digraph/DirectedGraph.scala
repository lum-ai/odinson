package ai.lum.odinson.digraph

/** Represents a directed graph (i.e. dependency parse).
 *
 *  @param incoming array whose elements correspond to the incoming edges of each token
 *                  and the edges are represented as an array where (node, label) pairs
 *                  have been flattened and label is represented as an id from a vocabulary.
 *  @param outgoing same as incoming
 *  @param roots root nodes
 */
case class DirectedGraph(
    incoming: Array[Array[Int]],
    outgoing: Array[Array[Int]],
    roots: Array[Int]
)
