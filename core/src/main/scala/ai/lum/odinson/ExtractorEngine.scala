package ai.lum.odinson

import java.io.File
import java.nio.file.Path

import org.apache.lucene.analysis.CharArraySet
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.document.{ Document => LuceneDocument }
import org.apache.lucene.search.{ BooleanClause => LuceneBooleanClause, BooleanQuery => LuceneBooleanQuery }
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.index.{ IndexReader, DirectoryReader }
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.highlight.TokenSources
import com.typesafe.config.Config
import ai.lum.common.ConfigUtils._
import ai.lum.common.StringUtils._
import ai.lum.odinson.compiler.QueryCompiler
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.analysis.TokenStreamUtils
import ai.lum.odinson.lucene.search._
import ai.lum.odinson.state.State
import ai.lum.odinson.utils.ConfigFactory



class ExtractorEngine(
  val indexReader: IndexReader,
  val indexSearcher: OdinsonIndexSearcher,
  val compiler: QueryCompiler,
  val state: State,
  val parentDocIdField: String
) {

  /** Analyzer for parent queries.  Don't skip any stopwords. */
  val analyzer = new WhitespaceAnalyzer()

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
    val q2 = new QueryParser("type", analyzer).parse("root")
    booleanQuery.add(q2, LuceneBooleanClause.Occur.MUST)
    val q = booleanQuery.build
    val docs = indexSearcher.search(q, 10).scoreDocs.map(sd => indexReader.document(sd.doc))
    //require(docs.size == 1, s"There should be only one parent doc for a docId, but ${docs.size} found.")
    docs.head
  }

  /** executes query and returns all results */
  def query(odinsonQuery: String): OdinResults = {
    query(odinsonQuery, indexReader.numDocs())
  }

  /** executes query and returns at most n documents */
  def query(odinsonQuery: String, n: Int): OdinResults = {
    query(compiler.mkQuery(odinsonQuery), n)
  }

  /** executes query and returns at most n documents */
  def query(odinsonQuery: String, parentQuery: String): OdinResults = {
    query(odinsonQuery, parentQuery, indexReader.numDocs())
  }

  /** executes query and returns at most n documents */
  def query(odinsonQuery: String, parentQuery: String, n: Int): OdinResults = {
    query(compiler.mkQuery(odinsonQuery, parentQuery), n)
  }

  /** executes query and returns at most n documents */
  def query(odinsonQuery: OdinsonQuery, n: Int): OdinResults = {
    indexSearcher.odinSearch(odinsonQuery, n)
  }

  /** executes query and returns next n results after the provided doc */
  def query(
    odinsonQuery: String,
    n: Int,
    afterDoc: Int,
    afterScore: Float
  ): OdinResults = {
    query(
      compiler.mkQuery(odinsonQuery),
      n,
      new OdinsonScoreDoc(afterDoc, afterScore)
    )
  }

  /** executes query and returns next n results after the provided doc */
  def query(
    odinsonQuery: String,
    parentQuery: String,
    n: Int,
    afterDoc: Int,
    afterScore: Float
  ): OdinResults = {
    query(
      compiler.mkQuery(odinsonQuery, parentQuery),
      n,
      new OdinsonScoreDoc(afterDoc, afterScore)
    )
  }

  /** executes query and returns next n results after the provided doc */
  def query(
    odinsonQuery: String,
    n: Int,
    after: OdinsonScoreDoc
  ): OdinResults = {
    query(compiler.mkQuery(odinsonQuery), n, after)
  }

  /** executes query and returns next n results after the provided doc */
  def query(
    odinsonQuery: String,
    parentQuery: String,
    n: Int,
    after: OdinsonScoreDoc
  ): OdinResults = {
    query(compiler.mkQuery(odinsonQuery, parentQuery), n, after)
  }

  /** executes query and returns next n results after the provided doc */
  def query(
    odinsonQuery: OdinsonQuery,
    n: Int,
    after: OdinsonScoreDoc
  ): OdinResults = {
    indexSearcher.odinSearch(after, odinsonQuery, n)
  }

  def getTokens(scoreDoc: OdinsonScoreDoc): Array[String] = {
    getTokens(scoreDoc.doc)
  }

  def getTokens(docID: Int): Array[String] = {
    val tokenField = "raw" // FIXME read from config?
    val doc = indexSearcher.doc(docID)
    val tvs = indexReader.getTermVectors(docID)
    val text = doc.getField(tokenField).stringValue
    val ts = TokenSources.getTokenStream(tokenField, tvs, text, analyzer, -1)
    val tokens = TokenStreamUtils.getTokens(ts)
    tokens
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
    val indexDir = config[Path]("indexDir")
    val indexReader = DirectoryReader.open(FSDirectory.open(indexDir))
    val indexSearcher = new OdinsonIndexSearcher(indexReader)
    val compiler = QueryCompiler.fromConfig(config[Config]("compiler"))
    val jdbcUrl = config[String]("state.jdbc.url")
    val state = new State(jdbcUrl)
    state.init()
    compiler.setState(state)
    val parentDocIdField: String = config[String]("index.documentIdField")
    new ExtractorEngine(indexReader, indexSearcher, compiler, state, parentDocIdField)
  }

}
