package ai.lum.odinson

import java.io.File
import java.nio.file.Path

import org.apache.lucene.analysis.CharArraySet
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.document.{ Document => LuceneDocument }
import org.apache.lucene.search.{ BooleanClause => LuceneBooleanClause, BooleanQuery => LuceneBooleanQuery }
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.QueryParser
import com.typesafe.config._
import ai.lum.common.ConfigUtils._
import ai.lum.common.StringUtils._
import ai.lum.odinson.compiler.QueryCompiler
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search._


class ExtractorEngine(val indexDir: Path) {

  def this(indexDir: File) = this(indexDir.toPath)

  val indexReader = DirectoryReader.open(FSDirectory.open(indexDir))
  val indexSearcher = new OdinsonIndexSearcher(indexReader)
  val compiler = QueryCompiler.fromConfig("odinson.compiler")

  /** Analyzer for parent queries.  Don't skip any stopwords. */
  val parentAnalyzer = new WhitespaceAnalyzer()

  /** The docId field for the parent document.
   *  Children retain a reference to their parent via this shared field.
   */
  val parentDocIdField: String = {
    val config = ConfigFactory.load()
    config[String]("odinson.index.documentIdField")
  }

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
    val q1 = new QueryParser(parentDocIdField, parentAnalyzer).parse(s""""$sterileDocID"""")
    booleanQuery.add(q1, LuceneBooleanClause.Occur.MUST)
    val q2 = new QueryParser("type", parentAnalyzer).parse("root")
    booleanQuery.add(q2, LuceneBooleanClause.Occur.MUST)
    val q = booleanQuery.build
    val docs = indexSearcher.search(q, 10).scoreDocs.map(sd => indexReader.document(sd.doc))
    //require(docs.size == 1, s"There should be only one parent doc for a docId, but ${docs.size} found.")
    docs.head
  }

  /** executes query and returns all results */
  def query(q: String): OdinResults = {
    query(compiler.mkQuery(q))
  }

  /** executes query and returns all results */
  def query(oq: OdinsonQuery): OdinResults = {
    query(oq, indexReader.numDocs())
  }

  /** executes query and returns at most n documents */
  def query(oq: String, pf: String, n: Int): OdinResults = {
    query(compiler.mkQuery(oq, pf), n)
  }

  /** executes query and returns at most n documents */
  def query(oq: String, n: Int): OdinResults = {
    query(compiler.mkQuery(oq), n)
  }

  /** executes query and returns at most n documents */
  def query(oq: OdinsonQuery, n: Int): OdinResults = {
    indexSearcher.odinSearch(oq, n)
  }

  /** executes query and returns the next n documents after the provided doc */
  def query(oq: String, n: Int, after: OdinsonScoreDoc): OdinResults = {
    query(compiler.mkQuery(oq), n, after)
  }

  /** executes query and returns the next n documents after the provided doc */
  def query(oq: OdinsonQuery, n: Int, after: OdinsonScoreDoc): OdinResults = {
    indexSearcher.odinSearch(after, oq, n)
  }

  /** executes query and returns next n results after the provided doc */
  def query(oq: String, n: Int, afterDoc: Int, afterScore: Float): OdinResults = {
    query(compiler.mkQuery(oq), n, afterDoc, afterScore)
  }

  /** executes query and returns next n results after the provided doc */
  def query(oq: OdinsonQuery, n: Int, afterDoc: Int, afterScore: Float): OdinResults = {
    query(oq, n, new OdinsonScoreDoc(afterDoc, afterScore))
  }

}
