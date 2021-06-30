package controllers

import java.io.File
import java.nio.file.Path
import javax.inject._
import scala.math._
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import play.api.Configuration
import play.api.http.ContentTypes
import play.api.libs.json._
import play.api.mvc._
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.lucene.document.{ Document => LuceneDocument }
import org.apache.lucene.index.{ DirectoryReader, MultiFields }
import org.apache.lucene.util.automaton.{ CompiledAutomaton, RegExp }
import com.typesafe.config.ConfigRenderOptions
import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.odinson.{
  BuildInfo,
  ExtractorEngine,
  Mention,
  NamedCapture,
  OdinsonMatch,
  Document => OdinsonDocument
}
import ai.lum.odinson.digraph.Vocabulary
import org.apache.lucene.store.FSDirectory
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search.{ OdinsonIndexSearcher, OdinsonQuery, OdinsonScoreDoc }
import com.typesafe.config.Config
import play.api.cache._
import utils.LuceneHelpers._

import scala.annotation.tailrec

@Singleton
class OdinsonController @Inject() (
  config: Config = ConfigFactory.load(),
  playConfig: Configuration,
  cache: AsyncCacheApi,
  cc: ControllerComponents
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {
  // before testing, we would create configs to pass to the constructor? write test for build like ghp's example
  private val indexPath = config.apply[File]("odinson.indexDir").toPath
  private val indexDir = FSDirectory.open(indexPath)
  private val indexReader = DirectoryReader.open(indexDir)
  private val computeTotalHits = config.apply[Boolean]("odinson.computeTotalHits")
  private val indexSearcher = new OdinsonIndexSearcher(indexReader, computeTotalHits)

  def newEngine(): ExtractorEngine = ExtractorEngine.fromDirectory(config, indexDir, indexSearcher)

  // format: off
  val docsDir              = config.apply[File]  ("odinson.docsDir")
  val docIdField           = config.apply[String]("odinson.index.documentIdField")
  val sentenceIdField      = config.apply[String]("odinson.index.sentenceIdField")
  val parentDocFileName    = config.apply[String]("odinson.index.parentDocFieldFileName")
  val wordTokenField       = config.apply[String]("odinson.displayField")
  val pageSize             = config.apply[Int]   ("odinson.pageSize")
  val posTagTokenField     = config.apply[String]("odinson.index.posTagTokenField")
  val vocabularyExpiry     = playConfig.get[Duration]("play.cache.vocabularyExpiry")
  // format: on

  /** Generate the Result for when a throwable exception is encountered */
  def describeNonFatal(e: Throwable): Result = {
    val stackTrace = ExceptionUtils.getStackTrace(e)
    val json = Json.toJson(Json.obj("error" -> stackTrace))
    BadRequest(json)
  }

  /** Refer to the standard error handler for try blocks that throw a NonFatal exception and expect a Result. */
  val handleNonFatal: PartialFunction[Throwable, Result] = {
    case NonFatal(e) => describeNonFatal(e)
  }

  /** Refer to the standard error handler for try blocks that throw a NonFatal exception and expect a Future[Result]. */
  val handleNonFatalInFuture: PartialFunction[Throwable, Future[Result]] = {
    case NonFatal(e) => Future(describeNonFatal(e))
  }

  /** Return a standard error handler for try blocks that throw a NullPointerException and expect a Result. */
  def mkHandleNullPointer(message: String): PartialFunction[Throwable, Result] = {
    case _: NullPointerException => InternalServerError(message)
  }

  //  val extractorEngine = opm.extractorEngineProvider()
  /** convenience methods for formatting Play 2 Json */
  implicit class JsonOps(json: JsValue) {

    def format(pretty: Option[Boolean]): Result = pretty match {
      case Some(true) => Ok(Json.prettyPrint(json)).as(ContentTypes.JSON)
      case _          => Ok(json).as(ContentTypes.JSON)
    }

  }

  def buildInfo(pretty: Option[Boolean]) = Action {
    Ok(BuildInfo.toJson).as(ContentTypes.JSON)
  }

  def configInfo(pretty: Option[Boolean]) = Action {
    val options = ConfigRenderOptions.concise.setJson(true)
    val json = Json.parse(config.root.render(options))
    json.format(pretty)
  }

  def numDocs = Action {
    val extractorEngine: ExtractorEngine = newEngine()
    Ok(extractorEngine.indexReader.numDocs.toString).as(ContentTypes.JSON)
  }

  class FrequencyTable(minIdx: Int, maxIdx: Int, reverse: Boolean) {

    private var freqs: List[(String, Long)] = Nil

    private def greaterThanOrEqualTo(reverse: Boolean)(first: Long, second: Long) =
      if (reverse) first <= second else first >= second

    private def greaterThan(reverse: Boolean)(first: Long, second: Option[Long]) =
      if (reverse) {
        // anything is less than a None
        first < second.getOrElse(Long.MaxValue)
      } else {
        // anything is greater than a None
        first > second.getOrElse(Long.MinValue)
      }

    private val geq: (Long, Long) => Boolean = greaterThanOrEqualTo(reverse)
    private val gt: (Long, Option[Long]) => Boolean = greaterThan(reverse)

    def update(newTerm: String, newFreq: Long): Unit = {
      // only update if new term is higher (or lower, if reversed) frequency as last member
      // OR if we don't have enough elements in our frequency table
      if (gt(newFreq, freqs.lastOption.map(_._2)) || freqs.length <= maxIdx) {
        // separate those in front of this value from those behind
        val (before, after) = freqs.partition { case (_, extantFreq) => geq(extantFreq, newFreq) }
        // secondary sorting feature is alphanumeric, same as the input order
        // therefore we can safely ignore everything we cut out after maxIdx
        freqs = ((before :+ (newTerm, newFreq)) ++ after).take(maxIdx + 1)
      }
    }

    def get: List[(String, Long)] = freqs.slice(minIdx, maxIdx + 1)

  }

  /** For a given term field, find the terms ranked min to max (inclusive, 0-indexed)
    * @param field The field to count (e.g., raw, token, lemma, tag, etc.)
    * @param group Optional second field to condition the field counts on.
    * @param filter Optional regular expression filter for terms within `field`
    * @param order "freq" for greatest-to least frequency (default), "alpha" for alphanumeric order
    * @param min Highest rank to return (0 is highest possible value).
    * @param max Lowest rank to return (e.g., 9).
    * @param scale "count" for raw frequencies (default), "log10" for log-transform, "percent" for percent of total.
    * @param reverse Whether to reverse the order before slicing between `min` and `max` (default false).
    * @param pretty Whether to pretty-print the JSON results.
    * @return JSON frequency table as an array of objects.
    */
  def termFreq(
    field: String,
    group: Option[String],
    filter: Option[String],
    order: Option[String],
    min: Option[Int],
    max: Option[Int],
    scale: Option[String],
    reverse: Option[Boolean],
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      try {
        // cutoff the results to the requested ranks
        val defaultMin = 0
        val defaultMax = 9

        val minIdx = min.getOrElse(defaultMin)
        val maxIdx = max.getOrElse(defaultMax)

        val extractorEngine: ExtractorEngine = newEngine()
        // ensure that the requested field exists in the index
        val fields = MultiFields.getFields(extractorEngine.indexReader)
        val fieldNames = fields.iterator.asScala.toList
        // if the field exists, find the frequencies of each term
        if (fieldNames contains field) {
          // find the frequency of all terms in this field
          val terms = fields.terms(field)
          val termsEnum = filter match {

            // get filtered terms only
            case Some(filterString) =>
              // check for proper regex
              val valid = {
                filterString.r // throws exception if invalid Scala regex (superset of Lucene regex)
                true
              }

              if (valid) {
                // NB: This is Lucene regex, *not* Perl-compatible
                // see https://www.elastic.co/guide/en/elasticsearch/reference/5.6/query-dsl-regexp-query.html#regexp-syntax
                // TODO: unify regex type with ruleFreq filter
                val automaton = new RegExp(filter.get).toAutomaton
                new CompiledAutomaton(automaton).getTermsEnum(terms)
              } else terms.iterator

            case _ =>
              // just go through all terms
              terms.iterator

          }

          val firstLevel = order match {
            // alphanumeric order as defined by Lucene's term order
            case Some("alpha") =>
              reverse match {
                // we must cycle through the whole set of terms and just keep the tail
                case Some(true) =>
                  // we don't know where the end is in advance, so we queue each freq as we go
                  val termFreqs = new scala.collection.mutable.Queue[(String, Long)]()

                  while (termsEnum.next() != null) {
                    val term = termsEnum.term.utf8ToString
                    termFreqs.enqueue((term, termsEnum.totalTermFreq))
                    // if we exceed the size we need, just throw the oldest away
                    if (termFreqs.size > maxIdx) termFreqs.dequeue()
                  }
                  termFreqs
                    .toIndexedSeq
                    .reverse
                    .slice(minIdx, maxIdx + 1)

                // just take the first (max + 1) items, since they are stored in that order
                case _ =>
                  val termFreqs = new FrequencyTable(
                    minIdx,
                    maxIdx,
                    reverse.getOrElse(false)
                  )

                  var i = 0
                  while (termsEnum.next() != null && i <= maxIdx) {
                    val term = termsEnum.term.utf8ToString
                    termFreqs.update(term, termsEnum.totalTermFreq)
                    i += 1
                  }
                  termFreqs.get
              }
            // if not alphanumeric (hence frequency), we go through all terms and compare frequencies
            case _ =>
              val termFreqs = new FrequencyTable(
                minIdx,
                maxIdx,
                reverse.getOrElse(false)
              )

              while (termsEnum.next() != null) {
                val term = termsEnum.term.utf8ToString
                // println(term)
                termFreqs.update(term, termsEnum.totalTermFreq)
              }
              termFreqs.get
          }

          // count instances of each pairing of `field` and `group` terms
          val groupedTerms =
            if (group.nonEmpty && fieldNames.contains(group.get)) {
              // this is O(awful) but will only be costly when (max - min) is large
              val pairs = for {
                (term1, _) <- firstLevel
                odinsonQuery = extractorEngine.compiler.mkQuery(s"""(?<term> [$field="$term1"])""")
                results = extractorEngine.query(odinsonQuery)
                scoreDoc <- results.scoreDocs
                eachMatch <- scoreDoc.matches
                term2 = extractorEngine.dataGatherer.getTokensForSpan(
                  scoreDoc.doc,
                  eachMatch,
                  group.get
                ).head
              } yield (term1, term2)
              // count instances of this pair of terms from `field` and `group`, respectively
              pairs.groupBy(identity).mapValues(_.size.toLong).toIndexedSeq
            } else firstLevel

          // order again if there's a secondary grouping variable
          val reordered = groupedTerms.sortBy { case (ser, conditionedFreq) =>
            ser match {
              case singleTerm: String =>
                (firstLevel.indexOf((singleTerm, conditionedFreq)), -conditionedFreq)
              case (term1: String, _: String) =>
                // find the total frequency of the term (ignoring group condition)
                val totalFreq = firstLevel.find(_._1 == term1).get._2
                (firstLevel.indexOf((term1, totalFreq)), -conditionedFreq)
            }
          }

          // transform the frequencies as requested, preserving order
          val scaled = scale match {
            case Some("log10") => reordered map { case (term, freq) => (term, log10(freq)) }
            case Some("percent") =>
              val countTotal = terms.getSumTotalTermFreq
              reordered map { case (term, freq) => (term, freq.toDouble / countTotal) }
            case _ => reordered.map { case (term, freq) => (term, freq.toDouble) }
          }

          // rearrange data into a Seq of Maps for Jsonization
          val jsonObjs = scaled.map { case (termGroup, freq) =>
            termGroup match {
              case singleTerm: String =>
                Json.obj("term" -> singleTerm.asInstanceOf[String], "frequency" -> freq)
              case (term1: String, term2: String) =>
                Json.obj("term" -> term1, "group" -> term2, "frequency" -> freq)
            }
          }
          Json.toJson(jsonObjs).format(pretty)
        } else {
          // the requested field isn't in this index
          Json.obj().format(pretty)
        }
      } catch handleNonFatal
    }
  }

  /** Convenience method to determine if a string matches a given regular expression.
    * @param s The String to be searched.
    * @param regex The regular expression against which `s` should be compared.
    * @return True if the whole expression matches.
    */
  private def isMatch(s: String, regex: Option[String]): Boolean = s.matches(regex.getOrElse(".*"))

  case class RuleFreqRequest(
    grammar: String,
    parentQuery: Option[String] = None,
    allowTriggerOverlaps: Option[Boolean] = None,
    // group: Option[String] = None,
    filter: Option[String] = None,
    order: Option[String] = None,
    min: Option[Int] = None,
    max: Option[Int] = None,
    scale: Option[String] = None,
    reverse: Option[Boolean] = None,
    pretty: Option[Boolean] = None
  )

  object RuleFreqRequest {
    implicit val fmt: OFormat[RuleFreqRequest] = Json.format[RuleFreqRequest]
    implicit val read: Reads[RuleFreqRequest] = Json.reads[RuleFreqRequest]
  }

  /** Count how many times each rule matches from the active grammar on the active dataset.
    * @param grammar An Odinson grammar.
    * @param parentQuery A Lucene query to filter documents (optional).
    * @param allowTriggerOverlaps Whether or not event arguments are permitted to overlap with the event's trigger.
    * @param filter Optional regular expression filter for the rule name.
    * @param order "freq" for greatest-to least frequency (default), "alpha" for alphanumeric order.
    * @param min Highest rank to return (0 is highest possible value).
    * @param max Lowest rank to return (e.g., 9).
    * @param scale "count" for raw frequencies (default), "log10" for log-transform, "percent" for percent of total.
    * @param reverse Whether to reverse the order before slicing between `min` and `max` (default false).
    * @param pretty Whether to pretty-print the JSON results.
    * @return JSON frequency table as an array of objects.
    */
  def ruleFreq() = Action { request =>
    val extractorEngine: ExtractorEngine = newEngine()
    val ruleFreqRequest = request.body.asJson.get.as[RuleFreqRequest]
    //println(s"GrammarRequest: ${gr}")
    val grammar = ruleFreqRequest.grammar
    val parentQuery = ruleFreqRequest.parentQuery
    val allowTriggerOverlaps = ruleFreqRequest.allowTriggerOverlaps.getOrElse(false)
    // TODO: Allow grouping factor: "ruleType" (basic or event), "accuracy" (wrong or right), others?
    // val group = gr.group
    val filter = ruleFreqRequest.filter
    val order = ruleFreqRequest.order
    val min = ruleFreqRequest.min
    val max = ruleFreqRequest.max
    val scale = ruleFreqRequest.scale
    val reverse = ruleFreqRequest.reverse
    val pretty = ruleFreqRequest.pretty
    try {
      // rules -> OdinsonQuery
      val baseExtractors = extractorEngine.ruleReader.compileRuleString(grammar)
      val composedExtractors = parentQuery match {
        case Some(pq) =>
          val cpq = extractorEngine.compiler.mkParentQuery(pq)
          baseExtractors.map(be => be.copy(query = extractorEngine.compiler.mkQuery(be.query, cpq)))
        case None => baseExtractors
      }

      val mentions: Seq[Mention] = {
        val iterator = extractorEngine.extractMentions(
          composedExtractors,
          numSentences = extractorEngine.numDocs(),
          allowTriggerOverlaps = allowTriggerOverlaps,
          disableMatchSelector = false
        )
        iterator.toVector
      }

      val ruleFreqs = mentions
        // rule name is all that matters
        .map(_.foundBy)
        // collect the instances of each rule's results
        .groupBy(identity)
        // filter the rules by name, if a filter was passed
        // NB: this is Scala style anchored regex, *not* Lucene's RegExp
        // TODO: unify regex style with that of termFreq's filter
        .filter { case (ruleName, ms) => isMatch(ruleName, filter) }
        // count how many matches for each rule
        .map { case (k, v) => k -> v.length }
        .toSeq

      // order the resulting frequencies as requested
      val ordered = order match {
        // alphabetical
        case Some("alpha") => ruleFreqs.sortBy { case (ruleName, _) => ruleName }
        // frequency (default)
        case _ => ruleFreqs.sortBy { case (ruleName, freq) => (-freq, ruleName) }
      }

      // reverse if necessary
      val reversed = reverse match {
        case Some(true) => ordered.reverse
        case _          => ordered
      }

      // Count instances of every rule
      val countTotal = reversed.map(_._2).sum

      // cutoff the results to the requested ranks
      val defaultMin = 0
      val defaultMax = 9
      val sliced =
        reversed.slice(min.getOrElse(defaultMin), max.getOrElse(defaultMax) + 1).toIndexedSeq

      // transform the frequencies as requested, preserving order
      val scaled = scale match {
        case Some("log10") => sliced map { case (rule, freq) => (rule, log10(freq)) }
        case Some("percent") =>
          sliced map { case (rule, freq) => (rule, freq.toDouble / countTotal) }
        case _ => sliced.map { case (rule, freq) => (rule, freq.toDouble) }
      }

      // rearrange data into a Seq of Maps for Jsonization
      val jsonObjs = scaled.map { case (ruleName, freq) =>
        Json.obj("term" -> ruleName, "frequency" -> freq)
      }

      Json.arr(jsonObjs).format(pretty)
    } catch handleNonFatal
  }

  /** Return `nBins` quantile boundaries for `data`. Each bin will have equal probability.
    * @param data The data to be binned.
    * @param nBins The number of quantiles (e.g. 4 for quartiles).
    * @param isContinuous True if the data is continuous (if it has been log10ed, for example)
    * @return A sequence of quantile boundaries which should be inclusive of all data.
    */
  def quantiles(data: Array[Double], nBins: Int, isContinuous: Option[Boolean]): Seq[Double] = {
    val sortedData = data.sorted
    // quantile boundaries expressed as percentiles
    val percentiles = (0 to nBins) map (_.toDouble / nBins)

    val bounds = percentiles.foldLeft(List.empty[Double])((res, percentile) => {
      // approximate index of this percentile
      val i = percentile * (sortedData.length - 1)
      // interpolate between the two values of `data` that `i` falls between
      val lowerBound = floor(i).toInt
      val upperBound = ceil(i).toInt
      val fractionalPart = i - lowerBound
      val interpolation = sortedData(lowerBound) * (1 - fractionalPart) +
        sortedData(upperBound) * fractionalPart
      // if data is count data, the boundaries should be on whole numbers
      val toAdd = isContinuous match {
        case Some(true) => interpolation
        case _          => round(interpolation).toDouble
      }
      // ensure that boundaries have a reasonable width to mitigate floating point errors
      // ensure no width-zero bins
      if (toAdd - res.headOption.getOrElse(-1.0) > 1e-12) {
        toAdd :: res
      } else {
        res
      }
    })

    bounds.reverse
  }

  /** Count the instances of @data that fall within each consecutive pair of bounds (lower-bound inclusive).
    * @param data The count/frequency data to be analyzed.
    * @param bounds The boundaries that define the bins used for histogram summaries.
    * @return The counts of `data` that fall into each bin.
    */
  def histify(data: List[Double], bounds: List[Double]): List[Long] = {
    @tailrec
    def iter(data: List[Double], bounds: List[Double], result: List[Long]): List[Long] =
      bounds match {
        // empty list can't be counted
        case Nil => Nil
        // the last item in the list -- all remaining data fall into the last bin
        case head :: Nil => data.size :: result
        // shave off the unallocated datapoints that fall under this boundary cutoff and count them
        case head :: tail =>
          val (leftward, rightward) = data.partition(_ < head)
          iter(rightward, tail, leftward.size :: result)
      }

    iter(data, bounds, List.empty[Long]).reverse
  }

  // helper function
  private def processCounts(
    frequencies: List[Double],
    bins: Option[Int],
    equalProbability: Option[Boolean],
    xLogScale: Option[Boolean]
  ): Seq[JsObject] = {
    // log10-transform the counts
    val scaledFreqs = xLogScale match {
      case Some(true) => frequencies.map(log10)
      case _          => frequencies
    }

    val nBins: Int =
      if (bins.getOrElse(-1) > 0) {
        // user-provided bin count
        bins.get
      } else if (equalProbability.getOrElse(false)) {
        // more bins for equal probability graph
        ceil(2.0 * pow(scaledFreqs.length, 0.4)).toInt
      } else {
        // Rice rule
        ceil(2.0 * cbrt(scaledFreqs.length)).toInt
      }

    val (max, min) = (scaledFreqs.max, scaledFreqs.min)

    // the boundaries of every bin (of length nBins + 1)
    val allBounds = equalProbability match {
      case Some(true) =>
        // use quantiles to equalize the probability of each bin
        quantiles(scaledFreqs.toArray, nBins, isContinuous = xLogScale)

      case _ =>
        // use an invariant bin width
        val rawBinWidth = (max - min) / nBins.toDouble
        val binWidth = if (xLogScale.getOrElse(false)) rawBinWidth else round(rawBinWidth)
        (0 until nBins).foldLeft(List(min.toDouble))((res, i) =>
          (binWidth + res.head) :: res
        ).reverse
    }

    // right-inclusive bounds (for counting bins)
    val rightBounds = allBounds.tail.toList // map (_ + epsilon)

    // number of each count falling into this bin
    val binCounts = histify(scaledFreqs, rightBounds)
    val totalCount = binCounts.sum.toDouble

    // unify allBounds and binCounts to generate one JSON object per bin
    for (i <- allBounds.init.indices) yield {
      val width = allBounds(i + 1) - allBounds(i)
      val x = allBounds(i)
      val y = equalProbability match {
        case Some(true) =>
          // bar AREA (not height) should be proportional to the count for this bin
          // thus it is density rather than probability or count
          if (totalCount > 0 & width > 0) binCounts(i) / totalCount / width else 0.0
        case _ =>
          // report the actual count (can be scaled by UI)
          binCounts(i).toDouble
      }
      Json.obj(
        "w" -> width,
        "x" -> x,
        "y" -> y
      )
    }
  }

  /** Return coordinates defining a histogram of counts/frequencies for a given field.
    * @param field The field to analyze, e.g. lemma.
    * @param bins The number of bins to use for data partitioning (optional).
    * @param equalProbability Use variable-width bins to equalize the probability of each bin (optional).
    * @param xLogScale `log10`-transform the counts of each term (optional).
    * @param pretty Whether to pretty-print the JSON returned by the function.
    * @return A JSON array of each bin, defined by width, lower bound (inclusive), and frequency.
    */
  def termHist(
    field: String,
    bins: Option[Int],
    equalProbability: Option[Boolean],
    xLogScale: Option[Boolean],
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      val extractorEngine: ExtractorEngine = newEngine()
      // ensure that the requested field exists in the index
      val fields = MultiFields.getFields(extractorEngine.indexReader)
      val fieldNames = fields.iterator.asScala.toList
      // if the field exists, find the frequencies of each term
      if (fieldNames contains field) {
        // find the frequency of all terms in this field
        val terms = fields.terms(field)
        val termsEnum = terms.iterator()
        val termFreqs = scala.collection.mutable.HashMap[String, Long]()
        while (termsEnum.next() != null) {
          val term = termsEnum.term.utf8ToString
          termFreqs.update(term, termsEnum.totalTermFreq)
        }
        val frequencies = termFreqs.values.toList.map(_.toDouble)

        val jsonObjs = processCounts(frequencies, bins, equalProbability, xLogScale)

        Json.arr(jsonObjs).format(pretty)
      } else {
        // the requested field isn't in this index
        Json.obj().format(pretty)
      }
    }
  }

  case class RuleHistRequest(
    grammar: String,
    parentQuery: Option[String] = None,
    allowTriggerOverlaps: Option[Boolean] = None,
    bins: Option[Int],
    equalProbability: Option[Boolean],
    xLogScale: Option[Boolean],
    pretty: Option[Boolean]
  )

  object RuleHistRequest {
    implicit val fmt: OFormat[RuleHistRequest] = Json.format[RuleHistRequest]
    implicit val read: Reads[RuleHistRequest] = Json.reads[RuleHistRequest]
  }

  /** Return coordinates defining a histogram of counts/frequencies of matches of each rule.
    * @param grammar An Odinson grammar.
    * @param parentQuery A Lucene query to filter documents (optional).
    * @param allowTriggerOverlaps Whether or not event arguments are permitted to overlap with the event's trigger.
    * @param bins Number of bins to cut the rule counts into.
    * @param equalProbability Whether to make bin widths variable to make them equally probable.
    * @param xLogScale `log10`-transform the counts of each rule (optional).
    * @param pretty Whether to pretty-print the JSON returned by the function.
    * @return A JSON array of each bin, defined by width, lower bound (inclusive), and frequency.
    */
  def ruleHist() = Action { request =>
    val extractorEngine: ExtractorEngine = newEngine()
    val ruleHistRequest = request.body.asJson.get.as[RuleHistRequest]
    val grammar = ruleHistRequest.grammar
    val parentQuery = ruleHistRequest.parentQuery
    val allowTriggerOverlaps = ruleHistRequest.allowTriggerOverlaps.getOrElse(false)
    val bins = ruleHistRequest.bins
    val equalProbability = ruleHistRequest.equalProbability
    val xLogScale = ruleHistRequest.xLogScale
    val pretty = ruleHistRequest.pretty
    try {
      // rules -> OdinsonQuery
      val baseExtractors = extractorEngine.ruleReader.compileRuleString(grammar)
      val composedExtractors = parentQuery match {
        case Some(pq) =>
          val cpq = extractorEngine.compiler.mkParentQuery(pq)
          baseExtractors.map(be => be.copy(query = extractorEngine.compiler.mkQuery(be.query, cpq)))
        case None => baseExtractors
      }

      val mentions: Seq[Mention] = {
        val iterator = extractorEngine.extractMentions(
          composedExtractors,
          numSentences = extractorEngine.numDocs(),
          allowTriggerOverlaps = allowTriggerOverlaps,
          disableMatchSelector = false
        )
        iterator.toVector
      }

      val frequencies = mentions
        // rule name is all that matters
        .map(_.foundBy)
        // collect the instances of each rule's results
        .groupBy(identity)
        // filter the rules by name, if a filter was passed
        // .filter{ case (ruleName, ms) => isMatch(ruleName, filter) }
        // count how many matches for each rule
        .map { case (k, v) => v.length.toDouble }
        .toList

      val jsonObjs = processCounts(frequencies, bins, equalProbability, xLogScale)

      Json.arr(jsonObjs).format(pretty)
    } catch handleNonFatal
  }

  /** Information about the current corpus. <br>
    * Directory name, num docs, num dependency types, etc.
    */
  def corpusInfo(pretty: Option[Boolean]) = Action.async {
    Future {
      val extractorEngine: ExtractorEngine = newEngine()
      val numDocs = extractorEngine.indexReader.numDocs
      val corpusDir = config.apply[File]("odinson.indexDir").getName
      val depsVocabSize = {
        loadVocabulary.terms.toSet.size
      }
      val fields = MultiFields.getFields(extractorEngine.indexReader)
      val fieldNames = fields.iterator.asScala.toList
      val storedFields =
        if (extractorEngine.numDocs < 1) {
          Nil
        } else {
          val firstDoc = extractorEngine.doc(0)
          firstDoc.iterator.asScala.map(_.name).toList
        }
      val tokenFields = extractorEngine.dataGatherer.storedFields
      val allFields = MultiFields.getFields(extractorEngine.indexReader)
      val allFieldNames = allFields.iterator.asScala.toList
      val docFields = allFieldNames diff tokenFields

      val json = Json.obj(
        "numDocs" -> numDocs,
        "corpus" -> corpusDir,
        "distinctDependencyRelations" -> depsVocabSize,
        "tokenFields" -> tokenFields,
        "docFields" -> docFields,
        "storedFields" -> storedFields
      )
      json.format(pretty)
    }
  }

  def getDocId(luceneDocId: Int): String = {
    val extractorEngine: ExtractorEngine = newEngine()
    val doc: LuceneDocument = extractorEngine.indexReader.document(luceneDocId)
    doc.getValues(docIdField).head
  }

  def getSentenceIndex(luceneDocId: Int): Int = {
    val extractorEngine: ExtractorEngine = newEngine()
    val doc = extractorEngine.indexReader.document(luceneDocId)
    // FIXME: this isn't safe
    doc.getValues(sentenceIdField).head.toInt
  }

  def loadVocabulary: Vocabulary = {
    val indexPath = config.apply[Path]("odinson.indexDir")
    val indexDir = FSDirectory.open(indexPath)
    Vocabulary.fromDirectory(indexDir)
  }

  /** Retrieves vocabulary of dependencies for the current index.
    */
  def dependenciesVocabulary(pretty: Option[Boolean]) = Action.async {
    Future {
      // NOTE: It's possible the vocabulary could change if the index is updated
      val vocabulary = loadVocabulary
      val vocab = vocabulary.terms.toList.sorted
      val json = Json.toJson(vocab)
      json.format(pretty)
    }
  }

  /** Return all terms for a given field in orthographic order.   *
    * @param field A token field such as word, lemma, or tag.
    * @return The complete [[List]] of terms in this field for the current index.
    */
  private def fieldVocabulary(field: String): List[String] = {
    // get terms from the requested field (error if it doesn't exist)
    val extractorEngine: ExtractorEngine = newEngine()
    val fields = MultiFields.getFields(extractorEngine.indexReader)
    val terms = fields.terms(field).map(_.utf8ToString).toList

    terms
  }

  /** Retrieves the POS tags for the current index (limited to extant tags).
    * @param pretty Whether to pretty-print the JSON results.
    * @return A JSON array of the tags in use in this index.
    */
  def tagsVocabulary(pretty: Option[Boolean]) = Action.async {
    // get ready to fail if tags aren't reachable
    try {
      cache.getOrElseUpdate[JsValue]("vocabulary.tags", vocabularyExpiry) {
        val tags = fieldVocabulary(posTagTokenField)
        val json = Json.toJson(tags)
        Future(json)
      }.map { json => json.format(pretty) }
    } catch handleNonFatalInFuture
  }

  /** Retrieves JSON for given sentence ID. <br>
    * Used to visualize parse and token attributes.
    */
  def sentenceJsonForSentId(sentenceId: Int, pretty: Option[Boolean]) = Action.async {
    Future {
      // ensure doc id is correct
      val json = mkAbridgedSentence(sentenceId)
      json.format(pretty)
    }
  }

  /** Stores query results in state.
    *
    * @param extractorEngine An extractor whose state should be altered.
    * @param odinsonQuery An Odinson pattern.
    * @param parentQuery A Lucene query to filter documents (optional).
    * @param label The label to use when committing matches.
    */
  def commitResults(
    extractorEngine: ExtractorEngine,
    odinsonQuery: String,
    parentQuery: Option[String],
    label: String = "Mention"
  ): Unit = {
    val results = parentQuery match {
      case None =>
        val q = extractorEngine.compiler.mkQuery(odinsonQuery)
        extractorEngine.query(q)
      case Some(filter) =>
        val q = extractorEngine.compiler.mkQuery(odinsonQuery, filter)
        extractorEngine.query(q)
    }
  }

  /** Queries the index.
    *
    * @param odisonQuery An OdinsonQuery
    * @param prevDoc The last Document ID seen on the previous page of results (required if retrieving page 2+).
    * @param prevScore The score of the last Document see on the previous page (required if retrieving page 2+).
    * @return JSON of matches
    */
  def retrieveResults(
    odinsonQuery: OdinsonQuery,
    prevDoc: Option[Int],
    prevScore: Option[Float]
  ): OdinResults = {
    val extractorEngine: ExtractorEngine = newEngine()
    (prevDoc, prevScore) match {
      case (Some(doc), Some(score)) =>
        val osd = new OdinsonScoreDoc(doc, score)
        extractorEngine.query(odinsonQuery, pageSize, osd)
      case _ =>
        extractorEngine.query(odinsonQuery, pageSize)
    }
  }

  case class GrammarRequest(
    grammar: String,
    parentQuery: Option[String] = None,
    pageSize: Option[Int] = None,
    allowTriggerOverlaps: Option[Boolean] = None,
    pretty: Option[Boolean] = None
  )

  object GrammarRequest {
    implicit val fmt: OFormat[GrammarRequest] = Json.format[GrammarRequest]
    implicit val read: Reads[GrammarRequest] = Json.reads[GrammarRequest]
  }

  /** Executes the provided Odinson grammar.
    *
    * @param grammar An Odinson grammar
    * @param parentQuery A Lucene query to filter documents (optional).
    * @param pageSize The maximum number of sentences to execute the rules against.
    * @param allowTriggerOverlaps Whether or not event arguments are permitted to overlap with the event's trigger.
    * @return JSON of matches
    */
  def executeGrammar() = Action { request =>
    //println(s"body: ${request.body}")
    //val json: JsValue = request.body.asJson.get
    val extractorEngine: ExtractorEngine = newEngine()
    // FIXME: replace .get with validation check
    val gr = request.body.asJson.get.as[GrammarRequest]
    //println(s"GrammarRequest: ${gr}")
    val grammar = gr.grammar
    val parentQuery = gr.parentQuery
    val pageSize = gr.pageSize
    val allowTriggerOverlaps = gr.allowTriggerOverlaps.getOrElse(false)
    val pretty = gr.pretty
    try {
      // rules -> OdinsonQuery
      val baseExtractors = extractorEngine.ruleReader.compileRuleString(grammar)
      val composedExtractors = parentQuery match {
        case Some(pq) =>
          val cpq = extractorEngine.compiler.mkParentQuery(pq)
          baseExtractors.map(be => be.copy(query = extractorEngine.compiler.mkQuery(be.query, cpq)))
        case None => baseExtractors
      }
      val start = System.currentTimeMillis()

      val maxSentences: Int = pageSize match {
        case Some(ps) => ps
        case None     => extractorEngine.numDocs()
      }

      val mentions: Seq[Mention] = {
        // FIXME: should deal in iterators to allow for, e.g., pagination...?
        val iterator = extractorEngine.extractMentions(
          composedExtractors,
          numSentences = maxSentences,
          allowTriggerOverlaps = allowTriggerOverlaps,
          disableMatchSelector = false
        )
        iterator.toVector
      }

      val duration = (System.currentTimeMillis() - start) / 1000f // duration in seconds

      val json = Json.toJson(mkJson(parentQuery, duration, allowTriggerOverlaps, mentions))
      json.format(pretty)
    } catch handleNonFatal
  }

  /** @param odinsonQuery An Odinson pattern
    * @param parentQuery A Lucene query to filter documents (optional).
    * @param label The label to use when committing matches to the state.
    * @param commit Whether or not results should be committed to the state.
    * @param prevDoc The last Document ID seen on the previous page of results (required if retrieving page 2+).
    * @param prevScore The score of the last Document see on the previous page (required if retrieving page 2+).
    * @return JSON of matches
    */
  def runQuery(
    odinsonQuery: String,
    parentQuery: Option[String],
    label: Option[String], // FIXME: in the future, this will be decided in the grammar
    commit: Option[Boolean], // FIXME: in the future, this will be decided in the grammar
    prevDoc: Option[Int],
    prevScore: Option[Float],
    enriched: Boolean,
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      try {
        val extractorEngine: ExtractorEngine = newEngine()

        val oq = parentQuery match {
          case Some(pq) =>
            extractorEngine.compiler.mkQuery(odinsonQuery, pq)
          case None =>
            extractorEngine.compiler.mkQuery(odinsonQuery)
        }
        val start = System.currentTimeMillis()
        val results: OdinResults = retrieveResults(oq, prevDoc, prevScore)
        val duration = (System.currentTimeMillis() - start) / 1000f // duration in seconds

        // should the results be added to the state?
        if (commit.getOrElse(false)) {
          // FIXME: can this be processed in the background?
          commitResults(
            extractorEngine = extractorEngine,
            odinsonQuery = odinsonQuery,
            parentQuery = parentQuery,
            label = label.getOrElse("Mention")
          )
        }

        val json = Json.toJson(mkJson(odinsonQuery, parentQuery, duration, results, enriched))
        json.format(pretty)
      } catch handleNonFatal
    }
  }

  def getMetadataJsonByDocumentId(
    documentId: String,
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      try {
        val odinsonDocument: OdinsonDocument = loadParentDocByDocumentId(documentId)
        val json: JsValue = Json.parse(odinsonDocument.toJson)("metadata")
        json.format(pretty)
      } catch mkHandleNullPointer(
        "This search index does not have document filenames saved as stored fields, so metadata cannot be retrieved."
      )
        .orElse(handleNonFatal)
    }
  }

  def getMetadataJsonBySentenceId(
    sentenceId: Int,
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      try {
        val extractorEngine: ExtractorEngine = newEngine()

        val luceneDoc: LuceneDocument = extractorEngine.doc(sentenceId)
        val documentId = luceneDoc.getValues(docIdField).head
        val odinsonDocument: OdinsonDocument = loadParentDocByDocumentId(documentId)
        val json: JsValue = Json.parse(odinsonDocument.toJson)("metadata")
        json.format(pretty)
      } catch mkHandleNullPointer(
        "This search index does not have document filenames saved as stored fields, so the parent document cannot be retrieved."
      )
        .orElse(handleNonFatal)
    }
  }

  def getParentDocJsonBySentenceId(
    sentenceId: Int,
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      try {
        val extractorEngine: ExtractorEngine = newEngine()
        val luceneDoc: LuceneDocument = extractorEngine.doc(sentenceId)
        val documentId = luceneDoc.getValues(docIdField).head
        val odinsonDocument: OdinsonDocument = loadParentDocByDocumentId(documentId)
        val json: JsValue = Json.parse(odinsonDocument.toJson)
        json.format(pretty)
      } catch mkHandleNullPointer(
        "This search index does not have document filenames saved as stored fields, so the parent document cannot be retrieved."
      )
        .orElse(handleNonFatal)
    }
  }

  def getParentDocJsonByDocumentId(
    documentId: String,
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      try {
        val odinsonDoc = loadParentDocByDocumentId(documentId)
        val json: JsValue = Json.parse(odinsonDoc.toJson)
        json.format(pretty)
      } catch mkHandleNullPointer(
        "This search index does not have document filenames saved as stored fields, so the parent document cannot be retrieved."
      )
        .orElse(handleNonFatal)
    }
  }

  def mkJson(
    odinsonQuery: String,
    parentQuery: Option[String],
    duration: Float,
    results: OdinResults,
    enriched: Boolean
  ): JsValue = {

    val scoreDocs: JsValue = enriched match {
      case true  => Json.arr(results.scoreDocs.map(mkJsonWithEnrichedResponse): _*)
      case false => Json.arr(results.scoreDocs.map(mkJson): _*)
    }

    Json.obj(
      // format: off
      "odinsonQuery" -> odinsonQuery,
      "parentQuery"  -> parentQuery,
      "duration"     -> duration,
      "totalHits"    -> results.totalHits,
      "scoreDocs"    -> scoreDocs
      // format: on
    )
  }

  /** Process results from executeGrammar */
  def mkJson(
    parentQuery: Option[String],
    duration: Float,
    allowTriggerOverlaps: Boolean,
    mentions: Seq[Mention]
  ): JsValue = {

    val mentionsJson: JsValue = Json.arr(mentions.map(mkJson): _*)

    Json.obj(
      // format: off
      "parentQuery"          -> parentQuery,
      "duration"             -> duration,
      "allowTriggerOverlaps" -> allowTriggerOverlaps,
      "mentions"             -> mentionsJson
      // format: on
    )
  }

  def mkJson(mention: Mention): Json.JsValueWrapper = {
    val extractorEngine: ExtractorEngine = newEngine()
    //val doc: LuceneDocument = extractorEngine.indexSearcher.doc(mention.luceneDocId)
    // We want **all** tokens for the sentence
    val tokens = extractorEngine.dataGatherer.getTokens(mention.luceneDocId, wordTokenField)
    // odinsonMatch: OdinsonMatch,
    Json.obj(
      // format: off
      "sentenceId"    -> mention.luceneDocId,
      // "score"         -> odinsonScoreDoc.score,
      "label"         -> mention.label,
      "documentId"    -> getDocId(mention.luceneDocId),
      "sentenceIndex" -> getSentenceIndex(mention.luceneDocId),
      "words"         -> JsArray(tokens.map(JsString)),
      "foundBy"       -> mention.foundBy,
      "match"         -> Json.arr(mkJson(mention.odinsonMatch))
      // format: on
    )
  }

  def mkJson(odinsonScoreDoc: OdinsonScoreDoc): Json.JsValueWrapper = {
    val extractorEngine: ExtractorEngine = newEngine()
    //val doc = extractorEngine.indexSearcher.doc(odinsonScoreDoc.doc)
    // we want **all** tokens for the sentence
    val tokens = extractorEngine.dataGatherer.getTokens(odinsonScoreDoc.doc, wordTokenField)
    Json.obj(
      // format: off
      "sentenceId"    -> odinsonScoreDoc.doc,
      "score"         -> odinsonScoreDoc.score,
      "documentId"    -> getDocId(odinsonScoreDoc.doc),
      "sentenceIndex" -> getSentenceIndex(odinsonScoreDoc.doc),
      "words"         -> JsArray(tokens.map(JsString)),
      "matches"       -> Json.arr(odinsonScoreDoc.matches.map(mkJson): _*)
      // format: on
    )
  }

  def mkJson(m: OdinsonMatch): Json.JsValueWrapper = {
    Json.obj(
      "span" -> Json.obj("start" -> m.start, "end" -> m.end),
      "captures" -> Json.arr(m.namedCaptures.map(mkJson): _*)
    )
  }

  def mkJson(namedCapture: NamedCapture): Json.JsValueWrapper = {
    Json.obj(namedCapture.name -> mkJson(namedCapture.capturedMatch))
  }

  def mkJsonWithEnrichedResponse(odinsonScoreDoc: OdinsonScoreDoc): Json.JsValueWrapper = {
    Json.obj(
      // format: off
      "sentenceId"    -> odinsonScoreDoc.doc,
      "score"         -> odinsonScoreDoc.score,
      "documentId"    -> getDocId(odinsonScoreDoc.doc),
      "sentenceIndex" -> getSentenceIndex(odinsonScoreDoc.doc),
      "sentence"      -> mkAbridgedSentence(odinsonScoreDoc.doc),
      "matches"       -> Json.arr(odinsonScoreDoc.matches.map(mkJson): _*)
      // format: on
    )
  }

  def loadParentDocByDocumentId(documentId: String): OdinsonDocument = {
    val extractorEngine: ExtractorEngine = newEngine()
    // lucene doc containing metadata
    val parentDoc: LuceneDocument = extractorEngine.getParentDoc(documentId)
    val odinsonDocFile = new File(docsDir, parentDoc.getField(parentDocFileName).stringValue)
    OdinsonDocument.fromJson(odinsonDocFile)
  }

  def retrieveSentenceJson(documentId: String, sentenceIndex: Int): JsValue = {
    val odinsonDoc: OdinsonDocument = loadParentDocByDocumentId(documentId)
    val docJson: JsValue = Json.parse(odinsonDoc.toJson)
    (docJson \ "sentences")(sentenceIndex)
  }

  def mkAbridgedSentence(sentenceId: Int): JsValue = {
    val sentenceIndex = getSentenceIndex(sentenceId)
    val documentId = getDocId(sentenceId)
    val unabridgedJson = retrieveSentenceJson(documentId, sentenceIndex)
    unabridgedJson
    //unabridgedJson.as[JsObject] - "startOffsets" - "endOffsets" - "raw"
  }

}
