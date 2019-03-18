package ai.lum.odinson.lucene.analysis

import scala.collection.mutable.ArrayBuffer
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute

object TokenStreamUtils {

  def getTokens(ts: TokenStream): Array[String] = {
    ts.reset()
    val terms = new ArrayBuffer[String]

    while (ts.incrementToken()) {
      val charTermAttribute = ts.addAttribute(classOf[CharTermAttribute])
      val term = charTermAttribute.toString
      terms += term
    }

    ts.end()
    ts.close()

    terms.toArray
  }

}
