package ai.lum.odinson.lucene.analysis

import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute

class OdinsonTokenStream(val tokens: Seq[String]) extends TokenStream {

  val termAtt = addAttribute(classOf[CharTermAttribute])

  private var tokenIndex: Int = 0

  override def reset(): Unit = {
    super.reset()
    tokenIndex = 0
  }

  final def incrementToken(): Boolean = {
    clearAttributes()
    if (tokenIndex >= tokens.length) return false
    termAtt.setEmpty().append(tokens(tokenIndex))
    tokenIndex += 1
    true
  }

}
