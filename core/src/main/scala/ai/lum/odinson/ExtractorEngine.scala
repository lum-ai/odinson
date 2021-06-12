package ai.lum.odinson

import java.io.File
import org.apache.lucene.document.{Document => LuceneDocument}
import org.apache.lucene.search.{BooleanClause => LuceneBooleanClause, BooleanQuery => LuceneBooleanQuery}
import org.apache.lucene.store.{Directory, FSDirectory}
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.QueryParser
import com.typesafe.config.{Config, ConfigValueFactory}
import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.common.StringUtils._
import ai.lum.odinson.DataGatherer.VerboseLevels
import ai.lum.odinson.DataGatherer.VerboseLevels.Verbosity
import ai.lum.odinson.compiler.QueryCompiler
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search._
import ai.lum.odinson.state.{MockState, State}
import ai.lum.odinson.digraph.Vocabulary
import ai.lum.odinson.plugins.motd.MOTDFactory
import ai.lum.odinson.utils.MostRecentlyUsed
import ai.lum.odinson.utils.exceptions.OdinsonException

import scala.collection.mutable.ArrayBuffer

class ExtractorEngine private (
  val indexSearcher: OdinsonIndexSearcher,
  val compiler: QueryCompiler,
  val dataGatherer: DataGatherer,
  val state: State, // todo: should this be private?
  val parentDocIdField: String
) {

  {
    // Just show an example of it happening.
    val motd = MOTDFactory.get()
    motd.show(System.out)
  }

  /** Analyzer for parent queries.  Don't skip any stopwords. */
  val analyzer = dataGatherer.analyzer

  val indexReader = indexSearcher.getIndexReader()

  val ruleReader = new RuleReader(compiler)

  // This boolean is for allowTriggerOverlaps.  This is so that we don't have to constantly check
  // allowTriggerOverlaps in an inner loop.  It's not going to change, after all.
  val filters: Map[Boolean, Mention => Option[Mention]] = Map(
    false -> { mention: Mention =>
      // If needed, filter results to discard trigger overlaps.
      mention.odinsonMatch match {
        case eventMatch: EventMatch =>
          eventMatch.removeTriggerOverlaps.map(eventMatch =>
            mention.copy(odinsonMatch = eventMatch)
          )
        case _ => Some(mention)
      }
    },
    true -> { mention: Mention =>
      Some(mention)
    }
  )

  /** Gets a lucene document id and returns the stored fields
    * corresponding to that document.
    *
    * @param docID lucene document id
    * @return a lucene document
    */
  def doc(docID: Int): LuceneDocument = {
    indexSearcher.doc(docID)
  }

  /** Returns the number of lucene documents in the index.
    * Note that this is not the same as the number of indexed sentences,
    * because there are also lucene documents indexed that store metadata.
    *
    * @return number of lucene documents in the index
    */
  def numDocs(): Int = {
    indexReader.numDocs()
  }

  /** ***********
    * These methods represent the ExtractorEngine first entrypoint,
    * that gets an OdinsonQuery and returns OdinResults.
    * This is meant to be similar to Lucene API.
    * **********
    */

  /** Executes an OdinsonQuery and returns an OdinResult
    * with all the matched documents and their corresponding matches.
    *
    * @param odinsonQuery
    * @return an OdinResult object
    */
  def query(odinsonQuery: OdinsonQuery): OdinResults = {
    query(odinsonQuery, false)
  }

  /** Executes an OdinsonQuery and returns an OdinResult
    * with all the matched documents and their corresponding matches.
    *
    * If disableMatchSelector is set to true, then the MatchSelector algorithm
    * will not be executed. This means that all the possible candidates for a match
    * will be returned, instead of just the correct one according to the query semantics,
    * e.g., select the longest match for the greedy quantifiers.
    *
    * If you don't know why you should disable the MatchSelector, then keep it enabled.
    *
    * @param odinsonQuery
    * @param disableMatchSelector
    * @return
    */
  def query(odinsonQuery: OdinsonQuery, disableMatchSelector: Boolean): OdinResults = {
    query(odinsonQuery, indexReader.numDocs(), disableMatchSelector)
  }

  /** Executes an OdinsonQuery and returns an OdinResult
    * with the first `n` matched lucene documents and their corresponding matches.
    * In this situation `n` can be considered to be the desired number of sentences to match.
    *
    * @param odinsonQuery
    * @param n
    * @return
    */
  def query(odinsonQuery: OdinsonQuery, n: Int): OdinResults = {
    query(odinsonQuery, n, false)
  }

  /** Executes an OdinsonQuery and returns an OdinResult
    * with the first `n` matched lucene documents and their corresponding matches.
    * In this situation `n` can be considered to be the desired number of sentences to match.
    *
    * If disableMatchSelector is set to true, then the MatchSelector algorithm
    * will not be executed. This means that all the possible candidates for a match
    * will be returned, instead of just the correct one according to the query semantics,
    * e.g., select the longest match for the greedy quantifiers.
    *
    * If you don't know why you should disable the MatchSelector, then keeep it enabled.
    *
    * @param odinsonQuery
    * @param n
    * @param disableMatchSelector
    * @return
    */
  def query(odinsonQuery: OdinsonQuery, n: Int, disableMatchSelector: Boolean): OdinResults = {
    query(odinsonQuery, n, null, disableMatchSelector)
  }

  /** Executes an OdinsonQuery and returns an OdinResult
    * with the next `n` matched lucene documents and their corresponding matches,
    * starting after the last lucene document in the OdinResults `after`.
    *
    * @param odinsonQuery
    * @param n number of desired lucene documents
    * @param after an OdinResults with a set of lucene documents
    * @return
    */
  def query(odinsonQuery: OdinsonQuery, n: Int, after: OdinResults): OdinResults = {
    query(odinsonQuery, n, after.scoreDocs.last)
  }

  /** Executes an OdinsonQuery and returns an OdinResult
    * with the next `n` matched lucene documents and their corresponding matches,
    * starting after the lucene document `after`.
    *
    * @param odinsonQuery
    * @param n number of desired lucene documents
    * @param after the last lucene document to ignore
    * @return
    */
  def query(odinsonQuery: OdinsonQuery, n: Int, after: OdinsonScoreDoc): OdinResults = {
    query(odinsonQuery, n, after, false)
  }

  /** Executes an OdinsonQuery and returns an OdinResult
    * with the next `n` matched lucene documents and their corresponding matches,
    * starting after the lucene document `after`.
    *
    * If disableMatchSelector is set to true, then the MatchSelector algorithm
    * will not be executed. This means that all the possible candidates for a match
    * will be returned, instead of just the correct one according to the query semantics,
    * e.g., select the longest match for the greedy quantifiers.
    *
    * If you don't know why you should disable the MatchSelector, then keep it enabled.
    *
    * @param odinsonQuery
    * @param n
    * @param after
    * @param disableMatchSelector
    * @return
    */
  def query(
    odinsonQuery: OdinsonQuery,
    n: Int,
    after: OdinsonScoreDoc,
    disableMatchSelector: Boolean
  ): OdinResults = {
    val odinResults =
      try {
        // we may need to read from the state as part of executing the query
        odinsonQuery.setState(Some(state))
        // actually execute the query
        indexSearcher.odinSearch(after, odinsonQuery, n, disableMatchSelector)
      } finally {
        // clean up after ourselves
        odinsonQuery.setState(None)
      }
    // return results
    odinResults
  }

  // FIXME rewrite this
  /** Retrieves the parent Lucene Document by docId */
  def getParentDoc(docId: String): LuceneDocument = {
    val sterileDocID = docId.escapeJava
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

  private def extract(
    extractor: Extractor,
    numSentences: Int,
    disableMatchSelector: Boolean,
    mruIdGetter: MostRecentlyUsed[Int, LazyIdGetter]
  ): Iterator[Mention] = {
    val odinResults = query(extractor.query, numSentences, null, disableMatchSelector)
    new MentionsIterator(
      extractor.label,
      Some(extractor.name),
      odinResults,
      mruIdGetter,
      Some(dataGatherer)
    )
  }

  /** Execute all rules in a grammar without populating the state.
    * This method will iterate once over all the rules, ignoring their priorities.
    * Returns an iterator over all the found Mentions.
    * Note that more than one mention may occurr in the same sentence.
    * Also note that "NoState" means the state won't be written, but may be read.
    *
    * @param extractors
    * @param allowTriggerOverlaps
    * @param disableMatchSelector
    * @return
    */
  def extractNoState(
    extractors: Seq[Extractor],
    allowTriggerOverlaps: Boolean = false,
    disableMatchSelector: Boolean = false
  ): Iterator[Mention] = {
    extractNoState(extractors, numDocs(), allowTriggerOverlaps, disableMatchSelector)
  }

  /** Execute all rules in a grammar without populating the state.
    * This method will iterate once over all the rules, ignoring their priorities.
    * Returns an iterator over the Mentions found in the first n matched sentences in the index.
    * Note that the number of mentions returned will be greater than or equal to numSentences.
    * Also note that "NoState" means the state won't be written, but may be read.
    *
    * @param extractors
    * @param numSentences
    * @param allowTriggerOverlaps
    * @param disableMatchSelector
    * @return
    */
  def extractNoState(
    extractors: Seq[Extractor],
    numSentences: Int,
    allowTriggerOverlaps: Boolean,
    disableMatchSelector: Boolean
  ): Iterator[Mention] = {
    val mruIdGetter = MostRecentlyUsed[Int, LazyIdGetter](LazyIdGetter(indexSearcher, _))

    // Apply each extractor, concatenate results
    val resultsIterators =
      extractors.map(extract(_, numSentences, disableMatchSelector, mruIdGetter))
    val results = MentionsIterator.concatenate(resultsIterators)

    // Apply the triggerOverlap filter, if enabled
    val filtered = filterMentions(results, allowTriggerOverlaps)

    // Handle argument promotion
    processAndPromote(filtered, usingState = false)
  }

  /** Execute all extractors in a grammar according to their priorities,
    * and writing their results to the State to be used by subsequent extractors.
    * Returns an iterator over the contents of the State.
    *
    * @param extractors
    * @param allowTriggerOverlaps
    * @param disableMatchSelector
    * @return
    */
  def extractMentions(
    extractors: Seq[Extractor],
    allowTriggerOverlaps: Boolean = false,
    disableMatchSelector: Boolean = false
  ): Iterator[Mention] = {
    extractMentions(extractors, numDocs(), allowTriggerOverlaps, disableMatchSelector)
  }

  /** Execute all extractors in a grammar according to their priorities,
    * and writing their results to the State to be used by subsequent extractors.
    * Returns an iterator over the contents of the State.
    *
    * @param extractors
    * @param numSentences
    * @param allowTriggerOverlaps
    * @param disableMatchSelector
    * @return
    */
  def extractMentions(
    extractors: Seq[Extractor],
    numSentences: Int,
    allowTriggerOverlaps: Boolean,
    disableMatchSelector: Boolean
  ): Iterator[Mention] = {
    // If there is a mock state, then we don't want to add mentions to the state, rather, we want to extract without a state
    // FIXME: maybe remove the mock state since we have "extractNoState" entry point?
    if (state.isInstanceOf[MockState.type]) {
      return extractNoState(extractors, numSentences, allowTriggerOverlaps, disableMatchSelector)
    }

    val minIterations = extractors.map(_.priority.minIterations).max
    val mruIdGetter = MostRecentlyUsed[Int, LazyIdGetter](LazyIdGetter(indexSearcher, _))

    var finished = false
    var epoch = 1

    while (!finished) {
      // extract the mentions from all extractors of this priority
      val mentions =
        extractFromPriority(epoch, extractors, numSentences, disableMatchSelector, mruIdGetter)
      epoch += 1
      // if anything returned, add to the state
      if (mentions.hasNext) {
        // future actions here
        // handle promotion
        // Filter any that are invalid and convert to Mentions
        val filtered = filterMentions(mentions, allowTriggerOverlaps)
        val processedMentions = processAndPromote(filtered, usingState = true)
        state.addMentions(processedMentions)
      } else if (epoch > minIterations) {
        // if nothing has been found and we've satisfied the minIterations, stop
        finished = true
      }
    }
    // At the end of the priorities, return all the Mentions found from the state
    state.getAllMentions()
  }

  /** Process each mention: if any of the arg mentions need to be promoted, bring them to the
    * top-level.  Also, if using the State, convert the odinsonMatches to StateMatches
    *
    * @param mentions
    * @param usingState
    * @return
    */
  private def processAndPromote(
    mentions: Iterator[Mention],
    usingState: Boolean
  ): Iterator[Mention] = {
    mentions.flatMap(m => handleArgumentPromotion(m, usingState))
  }

  /** Look into the Mention and bring any Mentions which need to be "promoted"
    * (i.e., added to the State) to the top-level.  Arguments are promoted if
    * (a) they were designated as such in the event rule, and (b) they are not already
    * in the State.  Also, in the process, if the state is in use, converts the
    * arguments AND the original, top-level mention to a State mention
    *
    * @param m Mention
    * @return original Mention plus any argument Mentions that need to be added to the State
    */
  private def handleArgumentPromotion(m: Mention, usingState: Boolean): Seq[Mention] = {
    // This will accrue the promoted argument mentions and original mention, all of which
    // will be converted to having StateMatches (if usingState)
    val results = new ArrayBuffer[Mention]()

    // Process and accrue the arguments
    m.odinsonMatch match {
      // Argument promotion only applies to EventMatches
      case em: EventMatch =>
        // gather the arguments which were specified to promote in the rule
        val argNamesToPromote = em.argumentMetadata
          .filter(_.promote == true)
          .map(_.name)
          .distinct
        // then, iterate through all the mention arguments, gather the mentions which
        // have the names found above, and if they weren't already in the state:
        //    1. transform them into StateMatches (if using State)
        //    2. promote
        argNamesToPromote.foreach { argName =>
          // If the arguments contains found Mentions for that argument:
          if (m.arguments.contains(argName)) {
            // Retrieve the Array of argument Mentions
            val argArray = m.arguments(argName)
            // Loop through the argument Mentions, handling each at a time
            var i = 0
            while (i < argArray.length) {
              val originalArgMention = argArray(i)
              // If it's not already a StateMatch handle it.  This check is helpful because
              // if it's already a state match then (a) it doesn't need to be promoted (i.e.,
              // added to the state, and (b) it doesn't need to be converted to a StateMatch
              if (!originalArgMention.odinsonMatch.isInstanceOf[StateMatch]) {
                // TODO: make sure works with nested (event, arg = event, all found in same priority
                if (usingState) {
                  // Convert the Mention into one with a StateMatch
                  val processedMention = toStateMention(originalArgMention)
                  // Replace the original, to make sure the pointers are valid
                  argArray(i) = processedMention
                  // Add to the results, so it gets promoted
                  results.append(processedMention)
                } else {
                  // Even if we're not using the state, we still need to promote
                  results.append(originalArgMention)
                }
              }
              i += 1
            }
          }
        }
      case _ => ()
    }

    // Add the top-level mention passed in, converting to a StateMatch if using the State
    if (usingState) {
      results.append(toStateMention(m))
    } else {
      results.append(m)
    }

    results
  }

  private def extractFromPriority(
    i: Int,
    extractors: Seq[Extractor],
    numSentences: Int,
    disableMatchSelector: Boolean,
    mruIdGetter: MostRecentlyUsed[Int, LazyIdGetter]
  ): Iterator[Mention] = {
    val resultsIterators = for {
      extractor <- extractors
      if extractor.priority matches i
    } yield extract(extractor, numSentences, disableMatchSelector, mruIdGetter)
    MentionsIterator.concatenate(resultsIterators)
  }

  private def filterMentions(
    ms: Iterator[Mention],
    allowTriggerOverlaps: Boolean
  ): Iterator[Mention] = {
    val filter = filters(allowTriggerOverlaps)
    for {
      m <- ms
      mention <- filter(m)
    } yield mention
  }

  def extractAndPopulate(
    extractors: Seq[Extractor],
    numSentences: Int = numDocs(),
    allowTriggerOverlaps: Boolean = false,
    disableMatchSelector: Boolean = false,
    level: Verbosity = VerboseLevels.Display
  ): Iterator[Mention] = {
    val mentions =
      extractMentions(extractors, numSentences, allowTriggerOverlaps, disableMatchSelector)
    // Each mention populates itself in place, returns for new iterator
    mentions.map { m =>
      m.populateFields(level)
      m
    }
  }

  /** Close the open resources.
    */
  def close(): Unit = {
    state.close()
  }

  // ----------------------------------------------
  //                  Manage State
  // ----------------------------------------------

  /** Clears the state, afterwards previously found mentions will not be available.
    */
  def clearState(): Unit = {
    state.clear()
  }

  /** Save the current state to the path provided.
    * @param path
    */
  def saveStateTo(path: String): Unit = {
    saveStateTo(new File(path))
  }

  /** Save the current state to the File provided
    * @param file
    */
  def saveStateTo(file: File): Unit = {
    state.dump(file)
  }

  // Convert the inner odinsonMatch to a StateMatch.  We don't need to do this
  // recursively bc an event's arguments inherently are already state mentions
  // either through promotion or being previously found.
  def toStateMention(mention: Mention): Mention = {
    val odinsonMatch = mention.odinsonMatch
    if (odinsonMatch.isInstanceOf[StateMatch]) {
      mention
    } else {
      val stateMatch = StateMatch.fromOdinsonMatch(odinsonMatch)
      mention.copy(odinsonMatch = stateMatch)
    }
  }

  // Methods to access DataGatherer

  @deprecated(
    message = "This method is deprecated, please use ai.lum.odinson.DataGatherer.getStringForSpan",
    since = "0.3.2"
  )
  def getStringForSpan(docID: Int, m: OdinsonMatch): String =
    dataGatherer.getStringForSpan(docID, m)

  @deprecated(
    message = "This method is deprecated, please use the `text()` method of the argument Mention",
    since = "0.3.2"
  )
  def getArgument(mention: Mention, name: String): String = dataGatherer.getArgument(mention, name)

  @deprecated(
    message = "This method is deprecated, please use Mention.mentionFields",
    since = "0.3.2"
  )
  def getTokensForSpan(m: Mention): Array[String] = dataGatherer.getTokensForSpan(m)

  @deprecated(
    message = "This method is deprecated, please use Mention.mentionFields",
    since = "0.3.2"
  )
  def getTokensForSpan(m: Mention, fieldName: String): Array[String] =
    dataGatherer.getTokensForSpan(m, fieldName)

  @deprecated(
    message = "This method is deprecated, please use ai.lum.odinson.DataGatherer.getTokensForSpan",
    since = "0.3.2"
  )
  def getTokensForSpan(docID: Int, m: OdinsonMatch): Array[String] =
    dataGatherer.getTokensForSpan(docID, m)

  @deprecated(
    message = "This method is deprecated, please use ai.lum.odinson.DataGatherer.getTokensForSpan",
    since = "0.3.2"
  )
  def getTokensForSpan(docID: Int, m: OdinsonMatch, fieldName: String): Array[String] =
    dataGatherer.getTokensForSpan(docID, m, fieldName)

  @deprecated(
    message = "This method is deprecated, please use ai.lum.odinson.DataGatherer.getTokensForSpan",
    since = "0.3.2"
  )
  def getTokensForSpan(docID: Int, start: Int, end: Int): Array[String] =
    dataGatherer.getTokensForSpan(docID, start, end)

  @deprecated(
    message = "This method is deprecated, please use ai.lum.odinson.DataGatherer.getTokensForSpan",
    since = "0.3.2"
  )
  def getTokensForSpan(docID: Int, fieldName: String, start: Int, end: Int): Array[String] =
    dataGatherer.getTokensForSpan(docID, fieldName, start, end)

  @deprecated(
    message = "This method is deprecated, please use ai.lum.odinson.DataGatherer.getTokens",
    since = "0.3.2"
  )
  def getTokens(scoreDoc: OdinsonScoreDoc): Array[String] = dataGatherer.getTokens(scoreDoc)

  @deprecated(
    message = "This method is deprecated, please use ai.lum.odinson.DataGatherer.getTokens",
    since = "0.3.2"
  )
  def getTokens(scoreDoc: OdinsonScoreDoc, fieldName: String): Array[String] =
    dataGatherer.getTokens(scoreDoc, fieldName)

  @deprecated(
    message = "This method is deprecated, please use ai.lum.odinson.DataGatherer.getTokens",
    since = "0.3.2"
  )
  def getTokens(docID: Int, fieldName: String): Array[String] =
    dataGatherer.getTokens(docID, fieldName)

}

object ExtractorEngine {
  lazy val defaultConfig: Config = ConfigFactory.load()

  def fromConfig(): ExtractorEngine = {
    fromConfig(defaultConfig)
  }

  def fromConfig(config: Config): ExtractorEngine = {
    val indexPath = config.apply[File]("odinson.indexDir").toPath
    val indexDir = FSDirectory.open(indexPath)
    fromDirectory(config, indexDir)
  }

  def fromDirectory(config: Config, indexDir: Directory): ExtractorEngine = {
    val indexReader = DirectoryReader.open(indexDir)
    val computeTotalHits = config.apply[Boolean]("odinson.computeTotalHits")
    val indexSearcher = new OdinsonIndexSearcher(indexReader, computeTotalHits)
    fromDirectory(config, indexDir, indexSearcher)
  }

  def fromDirectory(
    config: Config,
    indexDir: Directory,
    indexSearcher: OdinsonIndexSearcher
  ): ExtractorEngine = {
    val displayField = config.apply[String]("odinson.displayField")
    val dataGatherer = DataGatherer(indexSearcher.getIndexReader, displayField, indexDir)
    val vocabulary = Vocabulary.fromDirectory(indexDir)
    val compiler = QueryCompiler(config, vocabulary)
    val state = State(config, indexSearcher, indexDir)
    val parentDocIdField = config.apply[String]("odinson.index.documentIdField")
    new ExtractorEngine(
      indexSearcher,
      compiler,
      dataGatherer,
      state,
      parentDocIdField
    )
  }

  def inMemory(doc: Document): ExtractorEngine = {
    inMemory(Seq(doc))
  }

  def inMemory(docs: Seq[Document]): ExtractorEngine = {
    inMemory(ConfigFactory.load(), docs)
  }

  // Expecting a config that is already inside the `odinson` namespace
  def inMemory(config: Config, docs: Seq[Document]): ExtractorEngine = {
    // make a memory index
    val memWriter = OdinsonIndexWriter.inMemory(config)
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
