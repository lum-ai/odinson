package ai.lum.odinson.lucene.search.highlight

import org.apache.lucene.analysis.{ Analyzer, TokenStream }
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.index.IndexReader
import org.apache.lucene.search.highlight.TokenSources
import ai.lum.odinson.lucene.Span
import ai.lum.odinson.lucene.analysis.TokenStreamUtils


/**
  * Highlight results
  */
trait OdinsonHighlighter {

  val openTag: String
  val closeTag: String
  val argOpenTag: String

  def highlight(
    reader: IndexReader,
    docId: Int,
    field: String = "raw",
    analyzer: Analyzer = new WhitespaceAnalyzer(),
    spans: Seq[Span],
    captures: Seq[(String, Span)],
    openTag: String = openTag,
    argOpenTag: String = argOpenTag,
    closeTag: String = closeTag
  ): String = {

    // term vectors are computed on-the-fly if not already stored
    val tvs = reader.getTermVectors(docId)
    val doc = reader.document(docId)
    val sentenceText = doc.getField(field).stringValue
    val ts: TokenStream = TokenSources.getTokenStream(field, tvs, sentenceText, analyzer, -1)
    val tokens: Array[String] = TokenStreamUtils.getTokens(ts)

    if (captures.nonEmpty) {
      // highlight args
      for (capture <- captures) {
        val start = capture._2.start
        val end   = capture._2.end - 1

        val startTok  = tokens(start)
        tokens(start) = s"$argOpenTag$startTok"

        val endTok    = tokens(end)
        tokens(end)   = s"$endTok$closeTag"
      }

//      // mark start and end of mention
//      val allSpans     = {
//        captures.flatMap( c => Seq(c._2.start, c._2.end) ) ++
//          spans.flatMap( s => Seq(s.start, s.end))
//      }
//      val mentionStart = allSpans.min
//      val mentionEnd   = allSpans.max - 1
//
//      val startTok = tokens(mentionStart)
//      val endTok   = tokens(mentionEnd)
//
//      tokens(mentionStart) = s"$openTag$startTok"
//      tokens(mentionEnd)   = s"$endTok$closeTag"

    } else {
      for (span <- spans) {
        val start = span.start
        val end = span.end - 1

        val startTok  = tokens(start)
        tokens(start) = s"$openTag$startTok"

        val endTok    = tokens(end)
        tokens(end)   = s"$endTok$closeTag"
      }
    }


    tokens.mkString(" ")

  }

}


object HtmlHighlighter extends OdinsonHighlighter {

  override val openTag: String = "<mark class=\"odin-mention\">"
  override val closeTag: String = "</mark>"

  override val argOpenTag: String = "<mark class=\"odin-arg\">"
}

object ConsoleHighlighter extends OdinsonHighlighter {

  override val openTag: String =  Console.BLACK + Console.YELLOW_B
  override val closeTag: String = Console.RESET

  override val argOpenTag: String =  Console.BLACK + Console.RED_B

}
