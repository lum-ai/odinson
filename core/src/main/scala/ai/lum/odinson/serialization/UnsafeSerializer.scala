package ai.lum.odinson.serialization

import sun.misc.Unsafe
import java.util.Arrays
import ai.lum.odinson.digraph.DirectedGraph

class UnsafeSerializer(val bytes: Array[Byte]) {

  import UnsafeSerializer._

  private var pos = 0

  /** gets an int from bytes at the current position */
  def getInt(): Int = {
    val value = unsafe.getInt(bytes, byteArrayOffset + pos)
    pos += sizeOfInt
    value
  }

  /** puts an int in bytes at the current position */
  def putInt(value: Int): Unit = {
    unsafe.putInt(bytes, byteArrayOffset + pos, value)
    pos += sizeOfInt
  }

  /** gets an int array from bytes at the current position */
  def getIntArray(): Array[Int] = {
    val length = getInt()
    val values = new Array[Int](length)
    val bytesToCopy = length * sizeOfInt
    unsafe.copyMemory(
      bytes, byteArrayOffset + pos,
      values, intArrayOffset,
      bytesToCopy)
    pos += bytesToCopy
    values
  }

  /** put an int array in bytes at the current position */
  def putIntArray(values: Array[Int]): Unit = {
    val length = values.length
    val bytesToCopy = length * sizeOfInt
    putInt(length)
    unsafe.copyMemory(
      values, intArrayOffset,
      bytes, byteArrayOffset + pos,
      bytesToCopy)
    pos += bytesToCopy
  }

  /** get a FlatGraph from bytes at the current position */
  def getFlatGraph(): FlatGraph = {
    val incomingFlat = getIntArray()
    val incomingSlices = getIntArray()
    val outgoingFlat = getIntArray()
    val outgoingSlices = getIntArray()
    val roots = getIntArray()
    new FlatGraph(
      incomingFlat, incomingSlices,
      outgoingFlat, outgoingSlices,
      roots)
  }

  /** put a FlatGraph in bytes at the current position */
  def putFlatGraph(g: FlatGraph): Unit = {
    putIntArray(g.incomingFlat)
    putIntArray(g.incomingSlices)
    putIntArray(g.outgoingFlat)
    putIntArray(g.outgoingSlices)
    putIntArray(g.roots)
  }

  /** get a DirectedGraph from bytes at the current position */
  def getDirectedGraph(): DirectedGraph = {
    // incoming
    val nIncoming = getInt()
    val incoming = new Array[Array[Int]](nIncoming)
    var i = 0
    while (i < nIncoming) {
      incoming(i) = getIntArray()
      i += 1
    }
    // outgoing
    val nOutgoing = getInt()
    val outgoing = new Array[Array[Int]](nOutgoing)
    i = 0
    while (i < nOutgoing) {
      outgoing(i) = getIntArray()
      i += 1
    }
    // roots
    val roots = getIntArray()
    DirectedGraph(incoming, outgoing, roots)
  }

  /** put a DirectedGraph in bytes at the current position */
  def putDirectedGraph(g: DirectedGraph): Unit = {
    // incoming
    putInt(g.incoming.length)
    var i = 0
    while (i < g.incoming.length) {
      putIntArray(g.incoming(i))
      i += 1
    }
    // outgoing
    putInt(g.outgoing.length)
    i = 0
    while (i < g.outgoing.length) {
      putIntArray(g.outgoing(i))
      i += 1
    }
    // roots
    putIntArray(g.roots)
  }

  /** get FlatMap from bytes at current position and convert to DirectedGraph */
  def getFlatGraphAndUnflatten(): DirectedGraph = {
    val incomingFlat = getIntArray()
    val incomingSlices = getIntArray()
    val outgoingFlat = getIntArray()
    val outgoingSlices = getIntArray()
    val roots = getIntArray()
    // incoming
    val incoming = new Array[Array[Int]](incomingSlices.length / 2)
    var i = 0
    while (i < incomingSlices.length) {
      val from = incomingSlices(i)
      val to = incomingSlices(i + 1)
      if (from == to) {
        incoming(i / 2) = Array.emptyIntArray
      } else {
        incoming(i / 2) = Arrays.copyOfRange(incomingFlat, from, to)
      }
      i += 2
    }
    // outgoing
    val outgoing = new Array[Array[Int]](outgoingSlices.length / 2)
    i = 0
    while (i < outgoingSlices.length) {
      val from = outgoingSlices(i)
      val to = outgoingSlices(i + 1)
      if (from == to) {
        outgoing(i / 2) = Array.emptyIntArray
      } else {
        outgoing(i / 2) = Arrays.copyOfRange(outgoingFlat, from, to)
      }
      i += 2
    }
    DirectedGraph(incoming, outgoing, roots)
  }

}

object UnsafeSerializer {

  private val unsafe = {
    val field = classOf[Unsafe].getDeclaredField("theUnsafe")
    field.setAccessible(true)
    field.get(null).asInstanceOf[Unsafe]
  }

  val byteArrayOffset = unsafe.arrayBaseOffset(classOf[Array[Byte]])

  val intArrayOffset = unsafe.arrayBaseOffset(classOf[Array[Int]])

  /** size of an int in bytes */
  val sizeOfInt = 4

  class FlatGraph(
    val incomingFlat: Array[Int],
    val incomingSlices: Array[Int],
    val outgoingFlat: Array[Int],
    val outgoingSlices: Array[Int],
    val roots: Array[Int],
  )

  /** converts a DirectedGraph into an array of bytes */
  def graphToBytes(g: DirectedGraph): Array[Byte] = {
    val flat = flattenGraph(g)
    val size = sizeOfFlatGraph(flat)
    val bytes = new Array[Byte](size)
    val ser = new UnsafeSerializer(bytes)
    ser.putFlatGraph(flat)
    ser.bytes
  }

  def bytesToGraph(bytes: Array[Byte]): DirectedGraph = {
    val ser = new UnsafeSerializer(bytes)
    ser.getFlatGraphAndUnflatten()
  }

  /** converts a DirectedGraph into a FlatGraph for serialization */
  def flattenGraph(g: DirectedGraph): FlatGraph = {
    val incomingFlat = g.incoming.flatten
    val incomingSlices = new Array[Int](g.incoming.length * 2)
    val outgoingFlat = g.outgoing.flatten
    val outgoingSlices = new Array[Int](g.outgoing.length * 2)
    val roots = g.roots
    // populate incomingSlices
    var inStart = 0
    for (i <- g.incoming.indices) {
      val slice = i * 2
      val inStop = inStart + g.incoming(i).length
      incomingSlices(slice) = inStart
      incomingSlices(slice + 1) = inStop
      inStart = inStop
    }
    // populate outgoingSlices
    var outStart = 0
    for (i <- g.outgoing.indices) {
      val slice = i * 2
      val outStop = outStart + g.outgoing(i).length
      outgoingSlices(slice) = outStart
      outgoingSlices(slice + 1) = outStop
      outStart = outStop
    }
    new FlatGraph(
      incomingFlat, incomingSlices,
      outgoingFlat, outgoingSlices,
      roots)
  }

  /** returns the number of bytes required to store a FlatGraph */
  def sizeOfFlatGraph(g: FlatGraph): Int = {
    var size = 0
    size += sizeOfInt
    size += g.incomingFlat.length * sizeOfInt
    size += sizeOfInt
    size += g.incomingSlices.length * sizeOfInt
    size += sizeOfInt
    size += g.outgoingFlat.length * sizeOfInt
    size += sizeOfInt
    size += g.outgoingSlices.length * sizeOfInt
    size += sizeOfInt
    size += g.roots.length
    size
  }

}
