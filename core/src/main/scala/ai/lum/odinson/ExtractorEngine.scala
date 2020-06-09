package ai.lum.odinson

import java.nio.file.Path
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.document.{ Document => LuceneDocument }
import org.apache.lucene.search.{ BooleanClause => LuceneBooleanClause, BooleanQuery => LuceneBooleanQuery }
import org.apache.lucene.store.{ Directory, FSDirectory }
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



class ExtractorEngine(
  val indexSearcher: OdinsonIndexSearcher,
  val compiler: QueryCompiler,
  val displayField: String,
  val state: State,
  val parentDocIdField: String
) {

  /** Analyzer for parent queries.  Don't skip any stopwords. */
  val analyzer = new WhitespaceAnalyzer()

  val indexReader = indexSearcher.getIndexReader()

  val ruleReader = new RuleReader(compiler)

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

  def compileRules(rules: String): Seq[Extractor] = {
    compileRules(rules, Map.empty)
  }

  def compileRules(rules: String, variables: Map[String, String]): Seq[Extractor] = {
    ruleReader.compileRuleFile(rules, variables)
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
    // extract mentions
    val mentions = for {
      e <- extractors
      results = query(e.query, numSentences, disableMatchSelector)
      scoreDoc <- results.scoreDocs
      docFields = doc(scoreDoc.doc)
      docId = docFields.getField("docId").stringValue
      sentId = docFields.getField("sentId").stringValue
      odinsonMatch <- scoreDoc.matches
    } yield Mention(odinsonMatch, e.label, scoreDoc.doc, scoreDoc.segmentDocId, scoreDoc.segmentDocBase, docId, sentId, e.name)
    // if needed, filter results to discard trigger overlaps
    if (allowTriggerOverlaps) {
      mentions
    } else {
      mentions.flatMap { m =>
        m.odinsonMatch match {
          case e: EventMatch => e.removeTriggerOverlaps.map(e => m.copy(odinsonMatch = e))
          case _ => Some(m)
        }
      }
    }
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
  def query(
    odinsonQuery: OdinsonQuery,
    n: Int,
    after: OdinsonScoreDoc,
  ): OdinResults = {
    indexSearcher.odinSearch(after, odinsonQuery, n, false)
  }

  /** executes query and returns next n results after the provided doc */
  def query(
    odinsonQuery: OdinsonQuery,
    n: Int,
    after: OdinsonScoreDoc,
    disableMatchSelector: Boolean,
  ): OdinResults = {
    indexSearcher.odinSearch(after, odinsonQuery, n, disableMatchSelector)
  }

  def getString(docID: Int, m: OdinsonMatch): String = {
    getTokens(docID, m).mkString(" ")
  }

  def getTokens(m: Mention): Array[String] = {
    getTokens(m.luceneDocId, m.odinsonMatch)
  }

  def getTokens(docID: Int, m: OdinsonMatch): Array[String] = {
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

  def fromConfig(): ExtractorEngine = {
    fromConfig("odinson")
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

  def fromDirectory(config: Config, indexDir: Directory): ExtractorEngine = {
    val indexReader = DirectoryReader.open(indexDir)
    val computeTotalHits = config[Boolean]("computeTotalHits")
    val displayField = config[String]("displayField")
    val indexSearcher = new OdinsonIndexSearcher(indexReader, computeTotalHits)
    val vocabulary = Vocabulary.fromDirectory(indexDir)
    val compiler = QueryCompiler(config, vocabulary)
    val jdbcUrl = config[String]("state.jdbc.url")
    val state = new State(jdbcUrl)
    state.init()
    compiler.setState(state)
    val parentDocIdField = config[String]("index.documentIdField")
    new ExtractorEngine(
      indexSearcher,
      compiler,
      displayField,
      state,
      parentDocIdField
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
