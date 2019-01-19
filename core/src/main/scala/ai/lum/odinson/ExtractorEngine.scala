package ai.lum.odinson

import java.io.File
import java.nio.file.Path

import org.apache.lucene.analysis.CharArraySet
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.document.{Document => LuceneDocument}
import org.apache.lucene.search.{BooleanClause => LuceneBooleanClause, BooleanQuery => LuceneBooleanQuery}
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.QueryParser
import com.typesafe.config._
import ai.lum.common.ConfigUtils._
import ai.lum.common.StringUtils._
import ai.lum.odinson.compiler.QueryCompiler
import ai.lum.odinson.lucene._


class ExtractorEngine(val indexDir: Path) {

  val indexReader = DirectoryReader.open(FSDirectory.open(indexDir))
  val indexSearcher = new OdinIndexSearcher(indexReader)
  val compiler = QueryCompiler.fromConfig("odinson.compiler")

  /** Analyzer for parent queries.  Don't skip any stopwords. */
  val parentAnalyzer = new WhitespaceAnalyzer()

  /** The docId field for the parent document.  Children retain a reference to their parent via this shared field. */
  val parentDocIdField: String = {
    val config = ConfigFactory.load()
    config[String]("odinson.index.documentIdField")
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


  def query(q: OdinQuery, n: Int): OdinResults = {
    indexSearcher.odinSearch(q, n)
  }

  def query(q: OdinQuery, n: Int, after: OdinScoreDoc): OdinResults = {
    indexSearcher.odinSearch(after, q, n)
  }

  def query(oq: String, pq: Option[String]): OdinResults = {
    query(compiler.compile(oq, pq), indexReader.numDocs())
  }

  def query(q: OdinQuery): OdinResults = {
    query(q, indexReader.numDocs())
  }

  def query(oq: String, pq: Option[String], n: Int): OdinResults = {
    query(compiler.compile(oq, pq), n)
  }

  def query(oq: String, pq: Option[String], n: Int, after: OdinScoreDoc): OdinResults = {
    query(compiler.compile(oq, pq), n, after)
  }

  def query(oq: String, pq: Option[String], n: Int, afterDoc: Int, afterScore: Float): OdinResults = {
    query(compiler.compile(oq, pq), n, new OdinScoreDoc(afterDoc, afterScore))
  }

  def query(q: OdinQuery, n: Int, afterDoc: Int, afterScore: Float): OdinResults = {
    query(q, n, new OdinScoreDoc(afterDoc, afterScore))
  }

}

object ExtractorEngine {

  def apply(indexDir: String) = new ExtractorEngine(new File(indexDir).toPath)

}