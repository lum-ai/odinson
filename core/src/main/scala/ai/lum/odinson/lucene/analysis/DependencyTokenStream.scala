package ai.lum.odinson.lucene.analysis

import scala.annotation.tailrec
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.{ CharTermAttribute, PositionIncrementAttribute }

class DependencyTokenStream(val edges: Array[Array[(Int, String)]]) extends TokenStream {
  // edges can be either incoming or outgoing

  def this(edges: Seq[Seq[(Int, String)]]) = {
    this(edges.map(_.toArray).toArray)
  }

  val posIncrAtt = addAttribute(classOf[PositionIncrementAttribute])
  val termAtt = addAttribute(classOf[CharTermAttribute])

  private var tokenIndex: Int = 0
  private var labelIndex: Int = -1
  private var positionIncrement: Int = 1

  @tailrec
  final def incrementToken(): Boolean = {
    clearAttributes()
    if (tokenIndex >= edges.length) return false
    // Get labels of edges corresponding to current token.
    // Don't repeat labels.
    val labels = edges(tokenIndex).map(_._2).distinct
    labelIndex += 1
    if (labelIndex < labels.length) {
      if (labelIndex == 0) {
        // Increment position for the first label of the current token.
        posIncrAtt.setPositionIncrement(positionIncrement)
        positionIncrement = 1
      } else {
        // Store all labels for the current token in the same position.
        posIncrAtt.setPositionIncrement(0)
      }
      // Add edge label.
      val label = labels(labelIndex)
      termAtt.setEmpty()
      termAtt.append(label)
      true
    } else { // No more labels for the current token.
      if (labels.isEmpty) {
        // If the current token has no labels then we need to include it
        // in `positionIncrement` so that the next increment is correct.
        positionIncrement += 1
      }
      // Go to the next token.
      tokenIndex += 1
      labelIndex = -1
      incrementToken()
    }
  }

  override def reset(): Unit = {
    super.reset()
    tokenIndex = 0
    labelIndex = -1
    positionIncrement = 1
  }

}
