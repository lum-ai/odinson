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

  /** get a DirectedGraph from bytes at the current position */
  def getDirectedGraph(): DirectedGraph = {
    val incomingFlat = getIntArray()
    val incomingSlices = getIntArray()
    val outgoingFlat = getIntArray()
    val outgoingSlices = getIntArray()
    val roots = getIntArray()
    new DirectedGraph(
      incomingFlat, incomingSlices,
      outgoingFlat, outgoingSlices,
      roots)
  }

  /** put a DirectedGraph in bytes at the current position */
  def putDirectedGraph(g: DirectedGraph): Unit = {
    putIntArray(g.incomingFlat)
    putIntArray(g.incomingSlices)
    putIntArray(g.outgoingFlat)
    putIntArray(g.outgoingSlices)
    putIntArray(g.roots)
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

  /** converts a DirectedGraph into an array of bytes */
  def graphToBytes(g: DirectedGraph): Array[Byte] = {
    val size = sizeOfDirectedGraph(g)
    val bytes = new Array[Byte](size)
    val ser = new UnsafeSerializer(bytes)
    ser.putDirectedGraph(g)
    ser.bytes
  }

  def bytesToGraph(bytes: Array[Byte]): DirectedGraph = {
    val ser = new UnsafeSerializer(bytes)
    ser.getDirectedGraph()
  }

  /** returns the number of bytes required to store a DirectedGraph */
  def sizeOfDirectedGraph(g: DirectedGraph): Int = {
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
