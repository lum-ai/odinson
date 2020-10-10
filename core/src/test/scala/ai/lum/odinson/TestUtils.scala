package ai.lum.odinson

import com.typesafe.config.{Config, ConfigValueFactory}
import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.odinson.lucene._
import ai.lum.odinson.documentation.ExampleSentences
import ai.lum.odinson.state.NullIdGetter
import ai.lum.odinson.utils.MostRecentlyUsed

trait BaseFixtures {

  def getDocumentFromJson(json: String): Document = Document.fromJson(json)
  def getDocument(id: String): Document = getDocumentFromJson(ExampleSentences.json(id))

  val nullIdGetter = new NullIdGetter()
  val mruIdGetter = MostRecentlyUsed[Int, LazyIdGetter](NullIdGetter.apply)

  object Utils {
    val config = ConfigFactory.load()
    val odinsonConfig:Config = config[Config]("odinson")
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


    def extractorEngineWithSpecificState(doc: Document, provider: String): ExtractorEngine = {
      val newConfig = odinsonConfig.withValue("state.provider", ConfigValueFactory.fromAnyRef(provider))
      mkExtractorEngine(newConfig, doc)
    }

    /**
      * Constructs an [[ai.lum.odinson.ExtractorEngine]] from a single-doc in-memory index ([[org.apache.lucene.store.RAMDirectory]])
      */
    def mkExtractorEngine(doc: Document): ExtractorEngine = {
      ExtractorEngine.inMemory(doc)
    }

    def mkExtractorEngine(config: Config, doc: Document): ExtractorEngine = {
      ExtractorEngine.inMemory(config, Seq(doc))
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
