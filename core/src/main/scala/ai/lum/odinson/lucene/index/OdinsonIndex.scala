package ai.lum.odinson.lucene.index

import ai.lum.common.ConfigUtils._
import ai.lum.common.TryWithResources.using
import ai.lum.odinson.digraph.Vocabulary
import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.{ OdinsonQuery, OdinsonScoreDoc }
import ai.lum.odinson.utils.IndexSettings
import ai.lum.odinson.utils.exceptions.OdinsonException
import ai.lum.odinson.{ BuildInfo, LazyIdGetter, Document => OdinsonDocument }
import com.typesafe.config.Config
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.document.{ Document => LuceneDocument }
import org.apache.lucene.index.{ Fields, IndexReader, Term }
import org.apache.lucene.search.join.{
  QueryBitSetProducer,
  ScoreMode,
  ToChildBlockJoinQuery,
  ToParentBlockJoinQuery
}
import org.apache.lucene.search.{
  BooleanClause,
  BooleanQuery,
  Collector,
  CollectorManager,
  MatchAllDocsQuery,
  Query,
  TermQuery,
  TopDocs
}
import org.apache.lucene.store.{ Directory, FSDirectory, IOContext, RAMDirectory }

import java.nio.file.Paths

trait OdinsonIndex {

  val displayField: String
  val computeTotalHits: Boolean
  val normalizedTokenField: String
  val addToNormalizedField: Set[String]
  val incomingTokenField: String
  val outgoingTokenField: String
  val maxNumberOfTokensPerSentence: Int
  val invalidCharacterReplacement: String

  protected val VOCABULARY_FILENAME = "dependencies.txt"
  protected val BUILDINFO_FILENAME = "buildinfo.json"
  protected val SETTINGSINFO_FILENAME = "settingsinfo.json"

  val directory: Directory
  val settings: IndexSettings
  val analyzer: Analyzer = new WhitespaceAnalyzer

  val storedFields: Seq[String] = settings.storedFields
  val vocabulary = Vocabulary.fromDirectory(directory)

  def indexOdinsonDoc(doc: OdinsonDocument): Unit

  /** Removes all `org.apache.lucene.document.Document`s representing an [[ai.lum.odinson.Document]] (including metadata).
    * @param odinsonDocId The ID of the Odinson Document to remove from the index.
    */
  def deleteOdinsonDoc(odinsonDocId: String): Unit

  /** Updates index entries for an [[ai.lum.odinson.Document]] by first removing all `org.apache.lucene.document.Document`s representing an [[ai.lum.odinson.Document]] (including metadata) with the same ID.
    * @param odinsonDocId The ID of the Odinson Document to update from the index.
    */
  def updateOdinsonDoc(doc: OdinsonDocument): Unit

  /** Creates an `org.apache.lucene.search.Query` matching the final document in a block representing some [[ai.lum.odinson.Document]].  The match is based on the `ai.lum.odinson.Document.id` field.
    * @param odinsonDocId The ID of the Odinson Document associated with a block of Lucene documents.
    */
  def mkDocIdQueryFor(odinsonDocId: String): Query = {
    // query to identify the last lucene doc in the block
    val queryBuilder = new BooleanQuery.Builder()
    queryBuilder.add(
      new BooleanClause(
        new TermQuery(new Term(OdinsonIndexWriter.DOC_ID_FIELD, odinsonDocId)),
        BooleanClause.Occur.MUST
      )
    )
    queryBuilder.build()
  }

  /** Creates an `org.apache.lucene.search.Query` matching all but the final document in a block representing some [[ai.lum.odinson.Document]] (i.e., the children of that block).  The match is based on the `ai.lum.odinson.Document.id` field.
    * @param odinsonDocId The ID of the Odinson Document associated with a block of Lucene documents.
    */
  def mkChildrenQueryFor(odinsonDocId: String): Query = {
    val parentQuery = mkDocIdQueryFor(odinsonDocId)
    val parentsFilter = new QueryBitSetProducer(parentQuery)
    // identify all children in a doc based on a field in the final doc
    new ToChildBlockJoinQuery(parentQuery, parentsFilter)
  }

  /** Collects IDs for all `org.apache.lucene.document.Document`s representing some [[ai.lum.odinson.Document]] in the index.
    * @param odinsonDocId The ID of the Odinson Document for which to collect all of its component `org.apache.lucene.document.Document` IDs.
    */
  def luceneDocIdsFor(odinsonDocId: String): Seq[Int] = {
    // final lucene doc in block
    val parentQuery = mkDocIdQueryFor(odinsonDocId)
    // all but final lucene doc in block
    val childrenQuery = mkChildrenQueryFor(odinsonDocId)
    val res = search(childrenQuery).scoreDocs ++ search(parentQuery).scoreDocs
    // get IDs
    res.map(_.doc)
  }

  def write(block: java.util.Collection[LuceneDocument]): Unit

  // FIXME: use a constant value for default value of `limit` that represents largest possible value
  def search(query: Query, limit: Int = 1000000000): TopDocs

  def search[CollectorType <: Collector, ResultType](
    query: Query,
    manager: CollectorManager[CollectorType, ResultType]
  ): ResultType

  def search(
    scoreDoc: OdinsonScoreDoc,
    query: OdinsonQuery,
    cappedHits: Int,
    disableMatchSelector: Boolean
  ): OdinResults

  def numDocs(): Int

  def maxDoc(): Int

  def doc(docId: Int): LuceneDocument

  def doc(docId: Int, fieldNames: Set[String]): LuceneDocument

  def lazyIdGetter(luceneDocId: Int): LazyIdGetter

  def getTermVectors(docId: Int): Fields

  def getTokens(doc: LuceneDocument, termVectors: Fields, fieldName: String): Array[String]

  def getTokens(
    doc: LuceneDocument,
    tvs: Fields,
    fieldName: String,
    analyzer: Analyzer
  ): Array[String]

  def getTokensFromMultipleFields(docID: Int, fieldNames: Set[String]): Map[String, Array[String]]

  def refresh(): Unit

  def getIndexReader(): IndexReader

  def listFields(): Fields

  def dumpSettings(): Unit = {
    if (directory.listAll().contains(VOCABULARY_FILENAME)) directory.deleteFile(VOCABULARY_FILENAME)
    if (directory.listAll().contains(BUILDINFO_FILENAME)) directory.deleteFile(BUILDINFO_FILENAME)
    if (directory.listAll().contains(SETTINGSINFO_FILENAME))
      directory.deleteFile(SETTINGSINFO_FILENAME)

    // FIXME: is this the correct instantiation of IOContext?
    using(directory.createOutput(VOCABULARY_FILENAME, new IOContext)) { stream =>
      stream.writeString(vocabulary.dump)
    }
    using(directory.createOutput(BUILDINFO_FILENAME, new IOContext)) { stream =>
      stream.writeString(BuildInfo.toJson)
    }
    using(directory.createOutput(SETTINGSINFO_FILENAME, new IOContext)) { stream =>
      stream.writeString(settings.dump)
    }
  }

  def close(): Unit

}

object OdinsonIndex {

  def fromConfig(config: Config): OdinsonIndex = {

    val indexDir = config.apply[String]("odinson.indexDir")
    val (directory, vocabulary) = indexDir match {
      case ":memory:" =>
        // memory index is supported in the configuration file
        val dir = new RAMDirectory
        val vocab = Vocabulary.empty
        (dir, vocab)
      case path =>
        val dir = FSDirectory.open(Paths.get(path))
        val vocab = Vocabulary.fromDirectory(dir)
        (dir, vocab)
    }

    val storedFields = config.apply[List[String]]("odinson.index.storedFields")
    val displayField = config.apply[String]("odinson.displayField")
    // Always store the display field, also store these additional fields
    if (!storedFields.contains(displayField)) {
      throw new OdinsonException("`odinson.index.storedFields` must contain `odinson.displayField`")
    }

    val computeTotalHits = config.apply[Boolean]("odinson.computeTotalHits")

    val settings = IndexSettings(storedFields)
    val normalizedTokenField = config.apply[String]("odinson.index.normalizedTokenField")
    val addToNormalizedField =
      config.apply[List[String]]("odinson.index.addToNormalizedField").toSet
    val incomingTokenField = config.apply[String]("odinson.index.incomingTokenField")
    val outgoingTokenField = config.apply[String]("odinson.index.outgoingTokenField")
    val maxNumberOfTokensPerSentence =
      config.apply[Int]("odinson.index.maxNumberOfTokensPerSentence")
    val invalidCharacterReplacement =
      config.apply[String]("odinson.index.invalidCharacterReplacement")
    val refreshMs = {
      if (config.apply[Boolean]("odinson.index.incremental"))
        config.apply[Int]("odinson.index.refreshMs")
      else -1
    }

    new IncrementalOdinsonIndex(
      directory,
      settings,
      computeTotalHits,
      displayField,
      normalizedTokenField,
      addToNormalizedField,
      incomingTokenField,
      outgoingTokenField,
      maxNumberOfTokensPerSentence,
      invalidCharacterReplacement,
      refreshMs
    )

  }

  /** Context manager to ensure index gets closed.
    */
  def usingIndex[T](config: Config)(f: OdinsonIndex => T): T = using(fromConfig(config))(f)

}
