package ai.lum.odinson.lucene.analysis

import com.ibm.icu.text.Normalizer2
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.{ CharTermAttribute, PositionIncrementAttribute }

class NormalizedTokenStream(
  val tokenSeqs: Seq[Seq[String]],
  val normalizer: Normalizer2,
) extends TokenStream {

  val posIncrAtt = addAttribute(classOf[PositionIncrementAttribute])
  val termAtt = addAttribute(classOf[CharTermAttribute])

  /** Contains a collection of synonyms for each position in the sentence */
  private val synonymsPerToken: Vector[Vector[String]] = mkSynonyms(tokenSeqs)
  private var tokenIndex: Int = 0
  private var synonymIndex: Int = 0

  /** Gets several parallel sequences of tokens (e.g., words, tags, lemmas, etc)
   *  and groups them by position into collections of distinct normalized strings.
   *  The members of each of these collections will be considered synonyms.
   */
  private def mkSynonyms(tokenSeqs: Seq[Seq[String]]): Vector[Vector[String]] = {
    val synonyms = for (i <- tokenSeqs.head.indices) yield {
      tokenSeqs
        .map(tokens => normalizer.normalize(tokens(i)))
        .distinct
        .toVector
    }
    synonyms.toVector
  }

  override def reset(): Unit = {
    super.reset()
    tokenIndex = 0
    synonymIndex = 0
  }

  final def incrementToken(): Boolean = {
    clearAttributes()
    if (tokenIndex >= synonymsPerToken.length) return false
    // get synonyms corresponding to current position
    val synonyms = synonymsPerToken(tokenIndex)
    if (synonymIndex < synonyms.length) {
      if (synonymIndex == 0) {
        // only increase position for first synonym in group
        posIncrAtt.setPositionIncrement(1)
      } else {
        posIncrAtt.setPositionIncrement(0)
      }
      termAtt.setEmpty().append(synonyms(synonymIndex))
      synonymIndex += 1
      true
    } else {
      // move to next position in sentence
      tokenIndex += 1
      synonymIndex = 0
      incrementToken()
    }
  }

}
