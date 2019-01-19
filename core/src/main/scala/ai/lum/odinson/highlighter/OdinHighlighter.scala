package ai.lum.odinson.highlighter

import ai.lum.odinson.lucene.Span
import org.apache.lucene.analysis.{Analyzer, TokenStream}
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.index.IndexReader
import org.apache.lucene.search.highlight.TokenSources
import scala.collection.mutable.ListBuffer


/**
  * Highlight results
  */
trait OdinHighlighter {

  val openTag: String
  val closeTag: String

  def highlight(
    reader: IndexReader,
    docID: Int,
    field: String = "word",
    analyzer: Analyzer = new WhitespaceAnalyzer(),
    spans: Seq[Span],
    openTag: String = openTag,
    closeTag: String = closeTag
  ): String = {

    // term vectors are computed on-the-fly if not already stored
    val tvs = reader.getTermVectors(docID)
    val doc = reader.document(docID)
    val sentenceText = doc.getField(field).stringValue
    val ts: TokenStream = TokenSources.getTokenStream(field, tvs, sentenceText, analyzer, -1)
    val tokens: Array[String] = TokenStreamUtils.getTokens(ts)

    for (span <- spans) {
      val start = span.start
      val end = span.end - 1

      val startTok = tokens(start)
      tokens(start) = s"$openTag$startTok"

      val endTok = tokens(end)
      tokens(end) = s"$endTok$closeTag"
    }

    tokens.mkString(" ")

  }

}


object HtmlHighlighter extends OdinHighlighter {

  override val openTag: String = "<mark>"
  override val closeTag: String = "</mark>"

}

object ConsoleHighlighter extends OdinHighlighter {

  override val openTag: String =  Console.BLACK + Console.YELLOW_B
  override val closeTag: String = Console.RESET

}

object TokenStreamUtils {

  def getTokens(ts: TokenStream): Array[String] = {
    ts.reset()
    val terms = new ListBuffer[String]()

    while (ts.incrementToken()) {
      val charTermAttribute = ts.addAttribute(classOf[CharTermAttribute])
      val term = charTermAttribute.toString
      terms.append(term)
    }
    
    ts.end()
    ts.close()

    terms.toArray
  }

}
