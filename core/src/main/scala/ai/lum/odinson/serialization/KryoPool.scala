package ai.lum.odinson.serialization

import java.io.{ OutputStream, ByteArrayOutputStream }
import com.twitter.chill.{ ScalaKryoInstantiator, KryoPool, SerDeState }
import com.esotericsoftware.kryo.io.{ UnsafeInput, UnsafeOutput }

/** This is our fast serializer/deserializer for java objects.
 *  Odinson uses it for deserializing the dependency graph
 *  stored in the lucene index as a docvalue.
 */
class OdinKryoPool(poolSize: Int) extends KryoPool(poolSize) {

  private val ki = new ScalaKryoInstantiator

  protected def newInstance(): SerDeState = {
    new SerDeState(ki.newKryo(), new UnsafeInput(), new UnsafeOutput(new ByteArrayOutputStream())) {
      // we have to take extra care of the ByteArrayOutputStream
      override def clear(): Unit = {
        super.clear()
        val byteStream = output.getOutputStream().asInstanceOf[ByteArrayOutputStream]
        byteStream.reset()
      }
      override def outputToBytes(): Array[Byte] = {
        output.flush()
        val byteStream = output.getOutputStream().asInstanceOf[ByteArrayOutputStream]
        byteStream.toByteArray()
      }
      override def writeOutputTo(os: OutputStream): Unit = {
        output.flush()
        val byteStream = output.getOutputStream().asInstanceOf[ByteArrayOutputStream]
        byteStream.writeTo(os)
      }
    }
  }

}
