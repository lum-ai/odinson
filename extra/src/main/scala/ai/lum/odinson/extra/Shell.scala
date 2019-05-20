package ai.lum.odinson.extra

import java.io.File
import java.text.NumberFormat

import scala.util.control.NonFatal
import scala.collection.immutable.ListMap
import jline.console.ConsoleReader
import jline.console.history.FileHistory
import jline.console.completer.{ ArgumentCompleter, StringsCompleter }
import com.typesafe.config.Config
import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search._
import ai.lum.odinson.lucene.search.highlight.ConsoleHighlighter
import ai.lum.odinson.BuildInfo
import ai.lum.odinson.ExtractorEngine
import ai.lum.odinson.digraph.Vocabulary
import ai.lum.odinson.utils.ConfigFactory


object Shell extends App {

  // use ListMap to preserve commands order in `printHelp()`
  val commands = ListMap(
    ":help" -> "show commands",
    ":exit" -> "exit system",
    ":buildinfo" -> "build information",
    ":settings" -> "show settings (accepts optional scope)",
    ":corpus" -> "show some corpus statistics",
    ":more" -> "display a new results page",
    ":display" -> "specify the maximum number of matches to display (ex. :display 10)"
  )

  // read config parameters
  val config = ConfigFactory.load()
  var maxMatchesDisplay = config[Int]("odinson.shell.maxMatchesDisplay")
  val prompt = config[String]("odinson.shell.prompt")
  val history = new FileHistory(config[File]("odinson.shell.history"))

  // we must flush the history before exiting
  sys.addShutdownHook {
    history.flush()
  }

  // number formatter
  val intFormatter = NumberFormat.getIntegerInstance()
  val numFormatter = NumberFormat.getInstance()
  numFormatter.setMaximumFractionDigits(2)
  def fmt(n: Int): String = intFormatter.format(n.toLong)
  def fmt(n: Float): String = numFormatter.format(n.toDouble)

  // retrieve dependencies
  val dependenciesVocabulary = Vocabulary.fromIndex(config[File]("odinson.indexDir"))

  val dependencies = dependenciesVocabulary
    .terms
    .flatMap(dep => Seq(s">$dep", s"<$dep"))

  // autocomplete
  val autoCompleteOptions = dependencies.toList ++ commands.keys.toList
  val completer = new ArgumentCompleter(new StringsCompleter(autoCompleteOptions: _*))
  completer.setStrict(false)

  // setup console
  val reader = new ConsoleReader
  reader.setPrompt(prompt)
  reader.setHistory(history)
  reader.setExpandEvents(false)
  reader.addCompleter(completer)

  // setup searcher
  val extractorEngine = ExtractorEngine.fromConfig("odinson")

  // patterns to parse commands with arguments
  val matchNumResultsToDisplay = """^:display\s+(\d+)$""".r
  val matchSettingsScope = """^:settings\s+([\w\.-]+)$""".r

  var query: String = null
  var after: OdinsonScoreDoc = null
  var shownHits: Int = 0
  var totalHits: Int = 0

  // greetings
  val name = BuildInfo.name
  val version = BuildInfo.version
  val gitCommit = BuildInfo.gitHeadCommit.take(7)
  val gitDirty = if (BuildInfo.gitUncommittedChanges) "*" else ""
  println(s"Welcome to $name v$version ($gitCommit$gitDirty)")
  println("Type :help for a list of commands")

  try {
    // run the shell
    var running = true
    while (running) {
      try {
        val line = reader.readLine()
        if (line == null) {
          println(":exit")
          running = false
        } else {
          line.trim match {
            case "" => ()
            case ":help" => printHelp()
            case ":exit" => running = false
            case ":buildinfo" => printBuildInfo()
            case ":settings" => printSettings()
            case ":more" => printMore(maxMatchesDisplay)
            case ":corpus" =>
              println("Number of sentences: " + fmt(extractorEngine.numDocs()))
              // TODO maybe print some more stuff?
            case matchSettingsScope(s) => printSettings(s)
            case matchNumResultsToDisplay(n) =>
              maxMatchesDisplay = n.toInt
              println(s"will now display a maximum of $maxMatchesDisplay matches ...")
            case s if s startsWith ":" =>
              println(s"Unrecognized command $s")
              println("Type :help for a list of commands")
            case s if s startsWith "#" => ()
            case pattern =>
              query = pattern
              search(maxMatchesDisplay)
          }
        }
      } catch {
        // if the exception is non-fatal then display it and keep going
        case NonFatal(e) => e.printStackTrace()
      }
    }
  } finally {
    // manual terminal cleanup
    reader.getTerminal().restore()
    reader.shutdown()
  }

  /** Print shell's help message. */
  def printHelp(): Unit = {
    println("These are the commands at your disposal:\n")
    val longest = commands.keys.map(_.length).max
    for ((cmd, msg) <- commands) {
      val pad = " " * (longest - cmd.length)
      println(s" $cmd$pad => $msg")
    }
    println()
  }

  /** Print project's build information. */
  def printBuildInfo(): Unit = {
    println(s"Name: ${BuildInfo.name}")
    println(s"Version: ${BuildInfo.version}")
    println(s"Build date: ${BuildInfo.builtAtString}")
    print(s"Commit: ${BuildInfo.gitHeadCommit}")
    if (BuildInfo.gitUncommittedChanges) print(" (with uncommitted changes)")
    println()
    println(s"Scala version: ${BuildInfo.scalaVersion}")
    println(s"Sbt version: ${BuildInfo.sbtVersion}")
    println(s"Dependencies:")
    BuildInfo.libraryDependencies.foreach(s => println("  " + s))
    println(s"Scalac options:")
    BuildInfo.scalacOptions.foreach(s => println("  " + s))
    println()
  }

  def printSettings(): Unit = printSettings(config)
  def printSettings(s: String): Unit = printSettings(config[Config](s))
  def printSettings(c: Config): Unit = println(c.root().render())

  /** searches for pattern and prints the first n matches */
  def search(n: Int): Unit = {
    val start = System.currentTimeMillis()
    val results = extractorEngine.query(query, n)
    val duration = (System.currentTimeMillis() - start) / 1000f
    after = results.scoreDocs.lastOption.getOrElse(null)
    totalHits = results.totalHits
    shownHits = math.min(n, totalHits)
    printResultsPage(results, 1, totalHits, duration)
  }

  /** prints the next n matches */
  def printMore(n: Int): Unit = {
    if (after == null) {
      println("there is no active query")
      return
    }
    if (shownHits == totalHits) {
      println("no more results")
      return
    }
    val start = System.currentTimeMillis()
    val results = extractorEngine.query(query, n, after)
    val duration = (System.currentTimeMillis() - start) / 1000f
    after = results.scoreDocs.lastOption.getOrElse(null)
    if (after == null) {
      println("no more results")
      return
    }
    assert(totalHits == results.totalHits)
    printResultsPage(results, shownHits + 1, totalHits, duration)
    shownHits += math.min(n, totalHits)
  }

  /** prints a group of results */
  def printResultsPage(results: OdinResults, start: Int, total: Int, duration: Float): Unit = {
    if (total == 0) {
      println("no matches")
      return
    }
    val end = start + results.scoreDocs.length - 1
    println(s"found ${fmt(total)} matches in ${fmt(duration)} seconds")
    println(s"showing ${fmt(start)} to ${fmt(end)}\n")
    for (hit <- results.scoreDocs) {
      val doc = extractorEngine.doc(hit.doc)
      val docID = doc.getField("docId").stringValue
      println(s"Doc $docID (score = ${hit.score})")
      val spans = hit.matches.map(_.span).toVector
      val captures = hit.matches.flatMap(_.captures).toVector
      // FIXME: print statements used for debugging, please remove
      // println("spans: " + spans)
      // println("captures: " + captures)
      val res = ConsoleHighlighter.highlight(reader = extractorEngine.indexReader, docId = hit.doc, spans = spans, captures = captures)
      println(res)
      println()
    }
  }

}
