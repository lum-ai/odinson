package ai.lum.odinson

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
import ai.lum.odinson.state.StateFactory
import ai.lum.odinson.utils.OdinResultsIterator

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
    val minIterations = extractors.map(_.priority.minIterations).max
    // This is here both to demonstrate how a filter might be passed into the method.
    val filter = filters(allowTriggerOverlaps)

    def extract(i: Int, state: State): Seq[Mention] = for {
      e <- extractors
      if e.priority matches i
      odinResults = query(e.query, e.label, numSentences, null, disableMatchSelector, state)
      scoreDoc <- odinResults.scoreDocs
      docFields = doc(scoreDoc.doc)
      docId = docFields.getField("docId").stringValue
      sentId = docFields.getField("sentId").stringValue
      odinsonMatch <- scoreDoc.matches
      mention = mentionFactory.newMention(odinsonMatch, e.label, scoreDoc.doc, scoreDoc.segmentDocId, scoreDoc.segmentDocBase, docId, sentId, e.name)
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

  def explain(d: OdinsonScoreDoc, m: OdinsonMatch): Option[Explanation] = {
    m match {
      case m: GraphTraversalMatch =>
        val tokens = getTokens(d)
        val vocab = compiler.dependenciesVocabulary
        var path = ""
        var lexPath = ""
        var first = true
        for (step <- m.traversedPath.path) {
          val from = maybeQuoteWord(tokens(step.from))
          val to = maybeQuoteWord(tokens(step.to))
          val label = maybeQuoteLabel(vocab.getTerm(step.edgeLabel).get)
          val direction = if (step.direction == "incoming") "<" else ">"
          if (first) {
            path += s"$direction$label"
            lexPath += s"$direction$label"
            first = false
          } else {
            path += s" $direction$label"
            lexPath += s" $from $direction$label"
          }
        }
        val sentence = tokens.mkString(" ")
        val src = tokens.slice(m.srcMatch.start, m.srcMatch.end).mkString(" ")
        val dst = tokens.slice(m.dstMatch.start, m.dstMatch.end).mkString(" ")
        val explanation = new Explanation(sentence, src, dst, path, lexPath)
        Some(explanation)
      case _ => None
    }
  }

  // FIXME move this method to somewhere else
  // utils maybe?
  def maybeQuoteWord(s: String): String = {
    val isValidIdentifier = "^[a-zA-Z_][a-zA-Z0-9_]*$".r.findFirstIn(s).isDefined
    if (isValidIdentifier) s else "\"" + s.escapeJava + "\""
  }

  def maybeQuoteLabel(s: String): String = {
    val isValidIdentifier = "^[a-zA-Z_][a-zA-Z0-9_:-]*$".r.findFirstIn(s).isDefined
    if (isValidIdentifier) s else "\"" + s.escapeJava + "\""
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
      query(odinsonQuery, None, n, after, disableMatchSelector, state)
    }
  }

  /** executes query and returns next n results after the provided doc */
  def query(odinsonQuery: OdinsonQuery, label: Option[String] = None, n: Int, after: OdinsonScoreDoc, disableMatchSelector: Boolean, state: State): OdinResults = {
    val odinResults = try {
      odinsonQuery.setState(Some(state))
      indexSearcher.odinSearch(after, odinsonQuery, n, disableMatchSelector)
    }
    finally {
      odinsonQuery.setState(None)
    }
    // All of the odinResults will be added to the state, even though not all of them will
    // necessarily be used to create mentions.
    val odinResultsIterator = OdinResultsIterator(odinResults, label)
    state.addMentions(odinResultsIterator)

    odinResults
  }

  def getString(docID: Int, m: OdinsonMatch): String = {
    getTokens(docID, m).mkString(" ")
  }

  def getArgument(mention: Mention, name: String): String = {
    getString(mention.luceneDocId, mention.arguments(name).head.odinsonMatch)
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
  lazy val defaultMentionFactory = new DefaultMentionFactory()

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
