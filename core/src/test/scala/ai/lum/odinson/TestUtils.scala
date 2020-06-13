package ai.lum.odinson

import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.document.{ Document => LuceneDocument, _ }
import org.apache.lucene.document.Field.Store
import com.typesafe.config.Config
import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search._
import ai.lum.odinson.compiler.QueryCompiler
import ai.lum.odinson.state.State

trait BaseFixtures {

  def getDocumentFromJson(json: String): Document = Document.fromJson(json)
  
  object Utils {
    val config = ConfigFactory.load()
    val odinsonConfig = config[Config]("odinson")
    val rawTokenField = config[String]("odinson.index.rawTokenField")

    /**
      * Converts [[ai.lum.odinson.lucene.OdinResults]] to an array of strings.
      * Used to compare actual to expected results.
      */
    def mkStrings(results: OdinResults, engine: ExtractorEngine): Array[String] = {
      for {
        scoreDoc <- results.scoreDocs
        tokens = engine.getTokens(scoreDoc)
        swc <- scoreDoc.matches
      } yield {
        tokens
          .slice(swc.start, swc.end)
          .mkString(" ")
      }
    }

    /**
      * Constructs an [[ai.lum.odinson.ExtractorEngine]] from a single-doc in-memory index ([[org.apache.lucene.store.RAMDirectory]])
      */
    def mkExtractorEngine(doc: Document): ExtractorEngine = {
      ExtractorEngine.inMemory(doc)
    }

    def mkExtractorEngine(text: String): ExtractorEngine = {
      val tokens = TokensField(rawTokenField, text.split(" "), store = true)
      val sentence = Sentence(tokens.tokens.length, Seq(tokens))
      val document = Document("<TEST-ID>", Nil, Seq(sentence))
      mkExtractorEngine(document)
    }
  }
}

trait EventsFixtures {


}
