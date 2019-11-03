package ai.lum.odinson.digraph

/**
 * Represents a directed graph (i.e. dependency parse).
 *
 * @param incomingFlat array of ints that represents the incoming edges of each
 *        token where the slice corresponding to each token is given by
 *        `incomingSlices`, and the (node, label) pairs have been flattened and
 *        label is represented as an id from a vocabulary.
 * @param incomingSlices element `i` represents the start of the slice in
 *        `incoming` corresponding to token `i`, and `i+1` corresponds to one
 *        element after the last member of the slice.
 * @param outgoingFlat array of ints that represents the outgoing edges of each
 *        token where the slice corresponding to each token is given by
 *        `outgoingSlices`, and the (node, label) pairs have been flattened and
 *        label is represented as an id from a vocabulary.
 * @param outgoingSlices element `i` represents the start of the slice in
 *        `outgoing` corresponding to token `i`, and `i+1` corresponds to one
 *        element after the last member of the slice.
 * @param roots root nodes.
 */
class DirectedGraph(
    val incomingFlat: Array[Int],
    val incomingSlices: Array[Int],
    val outgoingFlat: Array[Int],
    val outgoingSlices: Array[Int],
    val roots: Array[Int],
)

object DirectedGraph {
  def mkGraph(
    incoming: Array[Array[Int]],
    outgoing: Array[Array[Int]],
    roots: Array[Int],
  ): DirectedGraph = {
    val incomingFlat = incoming.flatten
    val incomingSlices = new Array[Int](incoming.length + 1)
    val outgoingFlat = outgoing.flatten
    val outgoingSlices = new Array[Int](outgoing.length + 1)
    // populate incomingSlices
    var inStart = 0
    for (i <- incoming.indices) {
      incomingSlices(i) = inStart
      inStart += incoming(i).length
    }
    incomingSlices(incomingSlices.length - 1) = inStart
    // populate outgoingSlices
    var outStart = 0
    for (i <- outgoing.indices) {
      outgoingSlices(i) = outStart
      outStart += outgoing(i).length
    }
    outgoingSlices(outgoingSlices.length - 1) = outStart
    new DirectedGraph(
      incomingFlat, incomingSlices,
      outgoingFlat, outgoingSlices,
      roots)
  }
}
