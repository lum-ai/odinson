package ai.lum.odinson

import java.io.File
import java.nio.file.Path

import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.document.{Document => LuceneDocument}
import org.apache.lucene.search.{BooleanClause => LuceneBooleanClause, BooleanQuery => LuceneBooleanQuery}
import org.apache.lucene.store.{Directory, FSDirectory}
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.QueryParser
import com.typesafe.config.Config
import ai.lum.common.ConfigUtils._
import ai.lum.common.StringUtils._
import ai.lum.common.ConfigFactory
import ai.lum.odinson.compiler.QueryCompiler
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.analysis.TokenStreamUtils
import ai.lum.odinson.lucene.search._
import ai.lum.odinson.state.State
import ai.lum.odinson.digraph.Vocabulary
import ai.lum.odinson.state.FlatEventPromoter
import ai.lum.odinson.state.LabeledNamedOdinResults
import ai.lum.odinson.state.OdinResultsIterator
import ai.lum.odinson.state.StateFactory
import ai.lum.odinson.state.SuperOdinResultsIterator
import ai.lum.odinson.utils.MostRecentlyUsed


class LazyIdGetter(indexSearcher: OdinsonIndexSearcher, documentId: Int) extends IdGetter {
  protected lazy val document = indexSearcher.doc(documentId)
  protected lazy val docId: String = document.getField("docId").stringValue
  protected lazy val sentId: String = document.getField("sentId").stringValue

  def getDocId: String = docId

  def getSentId: String = sentId
}

object LazyIdGetter {
  def apply(indexSearcher: OdinsonIndexSearcher, docId: Int): LazyIdGetter = new LazyIdGetter(indexSearcher, docId)
}

class ExtractorEngine(
  val indexSearcher: OdinsonIndexSearcher,
  val compiler: QueryCompiler,
  val displayField: String,
  val stateFactory: StateFactory,
  val parentDocIdField: String,
  val mentionFactory: MentionFactory
) {

  /** Analyzer for parent queries.  Don't skip any stopwords. */
  val analyzer = new WhitespaceAnalyzer()

  val indexReader = indexSearcher.getIndexReader()

  val ruleReader = new RuleReader(compiler)

  val eventPromoter = new FlatEventPromoter()

  // This boolean is for allowTriggerOverlaps.  This is so that we don't have to constantly check
  // allowTriggerOverlaps in an inner loop.  It's not going to change, after all.
  val filters: Map[Boolean, Mention => Option[Mention]] = Map(
    false -> { mention: Mention =>
      // If needed, filter results to discard trigger overlaps.
      mention.odinsonMatch match {
        case eventMatch: EventMatch =>
          eventMatch.removeTriggerOverlaps.map(eventMatch => mention.copy(mentionFactory = mentionFactory, odinsonMatch = eventMatch))
        case _ => Some(mention)
      }
    },
    true -> { mention: Mention =>
      Some(mention)
    }
  )

  def doc(docID: Int): LuceneDocument = {
    indexSearcher.doc(docID)
  }

  def numDocs(): Int = {
    indexReader.numDocs()
  }

  /** Retrieves the parent Lucene Document by docId */
  def getParentDoc(docId: String): LuceneDocument = {
    val sterileDocID =  docId.escapeJava
    val booleanQuery = new LuceneBooleanQuery.Builder()
    val q1 = new QueryParser(parentDocIdField, analyzer).parse(s""""$sterileDocID"""")
    booleanQuery.add(q1, LuceneBooleanClause.Occur.MUST)
    val q2 = new QueryParser("type", analyzer).parse("metadata")
    booleanQuery.add(q2, LuceneBooleanClause.Occur.MUST)
    val q = booleanQuery.build
    val docs = indexSearcher.search(q, 10).scoreDocs.map(sd => indexReader.document(sd.doc))
    //require(docs.size == 1, s"There should be only one parent doc for a docId, but ${docs.size} found.")
    docs.head
  }

  // Access methods
  def compileRuleString(rules: String): Seq[Extractor] = {
    compileRuleString(rules, Map.empty[String, String])
  }

  def compileRuleString(rules: String, variables: Map[String, String]): Seq[Extractor] = {
    ruleReader.compileRuleString(rules, variables)
  }

  def compileRuleFile(ruleFile: File): Seq[Extractor] = {
    compileRuleFile(ruleFile, Map.empty[String, String])
  }

  def compileRuleFile(ruleFile: File, variables: Map[String, String]): Seq[Extractor] = {
    ruleReader.compileRuleFile(ruleFile, variables)
  }

  def compileRuleFile(rulePath: String): Seq[Extractor] = {
    compileRuleFile(rulePath, Map.empty[String, String])
  }

  def compileRuleFile(rulePath: String, variables: Map[String, String]): Seq[Extractor] = {
    ruleReader.compileRuleFile(rulePath, variables)
  }

  def compileRuleResource(rulePath: String): Seq[Extractor] = {
    compileRuleResource(rulePath, Map.empty[String, String])
  }

  def compileRuleResource(rulePath: String, variables: Map[String, String]): Seq[Extractor] = {
    ruleReader.compileRuleResource(rulePath, variables)
  }

  /** Apply the extractors and return all results */
  def extractMentions(extractors: Seq[Extractor], numSentences: Int): Seq[Mention] = {
    extractMentions(extractors, numSentences, false, false)
  }

  /** Apply the extractors and return all results */
  def extractMentions(extractors: Seq[Extractor], allowTriggerOverlaps: Boolean = false, disableMatchSelector: Boolean = false): Seq[Mention] = {
    extractMentions(extractors, numDocs(), allowTriggerOverlaps, disableMatchSelector)
  }

  /** Apply the extractors and return results for at most `numSentences` */
  def extractMentions(extractors: Seq[Extractor], numSentences: Int, allowTriggerOverlaps: Boolean, disableMatchSelector: Boolean): Seq[Mention] = {
    val minIterations = extractors.map(_.priority.minIterations).max
    // This is here both to demonstrate how a filter might be passed into the method.
    val filter = filters(allowTriggerOverlaps)
    val mruIdGetter = MostRecentlyUsed[Int, LazyIdGetter](LazyIdGetter(indexSearcher, _))

    def extract(i: Int, state: State): Seq[Mention] = for {
      extractor <- extractors
      if extractor.priority matches i
      odinResults = query(extractor.query, extractor.label, Some(extractor.name), numSentences, null, disableMatchSelector, state)
      scoreDoc <- odinResults.scoreDocs
      idGetter = mruIdGetter.getOrNew(scoreDoc.doc)
      odinsonMatch <- scoreDoc.matches
      mention = mentionFactory.newMention(odinsonMatch, extractor.label, scoreDoc.doc, scoreDoc.segmentDocId, scoreDoc.segmentDocBase, idGetter, extractor.name)
      mentionOpt = filter(mention)
      if (mentionOpt.isDefined)
    } yield mentionOpt.get

    @annotation.tailrec
    def loop(i: Int, mentions: Seq[Mention], state: State): Seq[Mention] = {
      val newMentions: Seq[Mention] = extract(i, state)

      if (0 < newMentions.length)
        loop(i + 1, newMentions ++: mentions, state) // TODO: Think about the order and efficiency.
      else if (i < minIterations)
        loop(i + 1, mentions, state)
      else
        mentions
    }

    val mentions = stateFactory.usingState { state =>
      loop(1, Seq.empty[Mention], state)
    }

    mentions.distinct
  }

  /** executes query and returns all results */
  def query(odinsonQuery: OdinsonQuery): OdinResults = {
    query(odinsonQuery, false)
  }

  /** executes query and returns all results */
  def query(odinsonQuery: OdinsonQuery, disableMatchSelector: Boolean): OdinResults = {
    query(odinsonQuery, indexReader.numDocs(), disableMatchSelector)
  }

  /** executes query and returns at most n documents */
  def query(odinsonQuery: OdinsonQuery, n: Int): OdinResults = {
    query(odinsonQuery, n, false)
  }

  /** executes query and returns at most n documents */
  def query(odinsonQuery: OdinsonQuery, n: Int, disableMatchSelector: Boolean): OdinResults = {
    query(odinsonQuery, n, null, disableMatchSelector)
  }

  /** executes query and returns next n results after the provided doc */
  def query(odinsonQuery: OdinsonQuery, n: Int, after: OdinsonScoreDoc): OdinResults = {
    query(odinsonQuery, n, after, false)
  }

  /** executes query and returns next n results after the provided doc */
  def query(odinsonQuery: OdinsonQuery, n: Int, after: OdinsonScoreDoc, disableMatchSelector: Boolean): OdinResults = {
    stateFactory.usingState { state =>
      query(odinsonQuery, None, None, n, after, disableMatchSelector, state)
    }
  }

  /** executes query and returns next n results after the provided doc */
  def query(odinsonQuery: OdinsonQuery, labelOpt: Option[String] = None, nameOpt: Option[String] = None, n: Int, after: OdinsonScoreDoc, disableMatchSelector: Boolean, state: State): OdinResults = {
    val odinResults: OdinResults = try {
      odinsonQuery.setState(Some(state))
      indexSearcher.odinSearch(after, odinsonQuery, n, disableMatchSelector)
    }
    finally {
      odinsonQuery.setState(None)
    }
    val labeledNamedOdinResults = LabeledNamedOdinResults(labelOpt, nameOpt, odinResults)
    val labeledNamedOdinResultsArr = eventPromoter.promoteEvents(labeledNamedOdinResults)
    val odinResultsIterator = new SuperOdinResultsIterator(labeledNamedOdinResultsArr)
    // If event promotion was not needed, this line could be used instead.
//     val odinResultsIterator = new OdinResultsIterator(labeledNamedOdinResults)

    state.addResultItems(odinResultsIterator)
    odinResults
  }

  @deprecated(message = "This signature of getString is deprecated and will be removed in a future release. Use getStringForSpan(docID: Int, m: OdinsonMatch) instead.", since = "https://github.com/lum-ai/odinson/commit/89ceb72095d603cf61d27decc7c42c5eea50c87a")
  def getString(docID: Int, m: OdinsonMatch): String = {
    getTokens(docID, m).mkString(" ")
  }

  def getStringForSpan(docID: Int, m: OdinsonMatch): String = {
    getTokensForSpan(docID, m).mkString(" ")
  }

  def getArgument(mention: Mention, name: String): String = {
    getStringForSpan(mention.luceneDocId, mention.arguments(name).head.odinsonMatch)
  }

  @deprecated(message = "This signature of getTokens is deprecated and will be removed in a future release. Use getTokensForSpan(m: Mention) instead.", since = "https://github.com/lum-ai/odinson/commit/89ceb72095d603cf61d27decc7c42c5eea50c87a")
  def getTokens(m: Mention): Array[String] = {
    getTokens(m.luceneDocId, m.odinsonMatch)
  }

  def getTokensForSpan(m: Mention): Array[String] = {
    getTokensForSpan(m.luceneDocId, m.odinsonMatch)
  }

  @deprecated(message = "This signature of getTokens is deprecated and will be removed in a future release. Use getTokensForSpan(docID: Int, m: OdinsonMatch) instead.", since = "https://github.com/lum-ai/odinson/commit/89ceb72095d603cf61d27decc7c42c5eea50c87a")
  def getTokens(docID: Int, m: OdinsonMatch): Array[String] = {
    getTokens(docID, displayField).slice(m.start, m.end)
  }

  def getTokensForSpan(docID: Int, m: OdinsonMatch): Array[String] = {
    getTokens(docID, displayField).slice(m.start, m.end)
  }

  def getTokens(scoreDoc: OdinsonScoreDoc): Array[String] = {
    getTokens(scoreDoc.doc, displayField)
  }

  def getTokens(scoreDoc: OdinsonScoreDoc, fieldName: String): Array[String] = {
    getTokens(scoreDoc.doc, fieldName)
  }

  def getTokens(docID: Int, fieldName: String): Array[String] = {
    TokenStreamUtils.getTokens(docID, fieldName, indexSearcher, analyzer)
  }

}

object ExtractorEngine {
  val defaultPath = "odinson"

  lazy val defaultMentionFactory = new DefaultMentionFactory()
  lazy val defaultConfig = ConfigFactory.load()[Config](defaultPath)

  def fromConfig(): ExtractorEngine = {
    fromConfig(defaultPath)
  }

  def fromConfig(path: String): ExtractorEngine = {
    val config = ConfigFactory.load()
    fromConfig(config[Config](path))
  }

  def fromConfig(config: Config): ExtractorEngine = {
    val indexPath = config[Path]("indexDir")
    val indexDir = FSDirectory.open(indexPath)
    fromDirectory(config, indexDir)
  }

  def fromDirectory(config: Config, indexDir: Directory, mentionFactory: MentionFactory = defaultMentionFactory): ExtractorEngine = {
    val indexReader = DirectoryReader.open(indexDir)
    val computeTotalHits = config[Boolean]("computeTotalHits")
    val displayField = config[String]("displayField")
    val indexSearcher = new OdinsonIndexSearcher(indexReader, computeTotalHits)
    val vocabulary = Vocabulary.fromDirectory(indexDir)
    val compiler = QueryCompiler(config, vocabulary)
    val stateFactory = StateFactory(config)
    val parentDocIdField = config[String]("index.documentIdField")
    new ExtractorEngine(
      indexSearcher,
      compiler,
      displayField,
      stateFactory,
      parentDocIdField,
      mentionFactory
    )
  }

  def inMemory(doc: Document): ExtractorEngine = {
    inMemory(Seq(doc))
  }

  def inMemory(docs: Seq[Document]): ExtractorEngine = {
    inMemory("odinson", docs)
  }

  def inMemory(path: String, docs: Seq[Document]): ExtractorEngine = {
    val config = ConfigFactory.load()
    inMemory(config[Config](path), docs)
  }

  def inMemory(config: Config, docs: Seq[Document]): ExtractorEngine = {
    // make a memory index
    val memWriter = OdinsonIndexWriter.inMemory
    // add documents to index
    for (doc <- docs) {
      val block = memWriter.mkDocumentBlock(doc)
      memWriter.addDocuments(block)
    }
    // finalize index writer
    memWriter.commit()
    memWriter.close()
    // return extractor engine
    ExtractorEngine.fromDirectory(config, memWriter.directory)
  }

}
