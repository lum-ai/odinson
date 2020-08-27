package ai.lum.odinson

import java.io.File
import java.util.{Collection, Map => JMap}

import ai.lum.common.TryWithResources.using

import scala.collection.JavaConverters._
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import ai.lum.odinson.compiler.QueryCompiler
import ai.lum.odinson.lucene.search.OdinsonQuery
import ai.lum.odinson.utils.{RuleSources, SituatedStream, VariableSubstitutor}

/** A RuleFile is the result of parsing a yaml file.
 *  At this point variables haven't been replaced
 *  and patterns haven't been compiled
 */
case class RuleFile(
  rules: Seq[Rule],
  variables: Map[String, String],
)

/** A Rule represents a single rule parsed from a yaml file.
 *  Its variables haven't been replaced and its pattern.
 *  hasn't been compiled.
 */
case class Rule(
  name: String,
  label: Option[String],
  ruletype: String,
  priority: String,
  pattern: String,
)

/** An Extractor is a compiled Rule.
 *  It is ready to be executed by the ExtractionEngine.
 */
case class Extractor(
  name: String,
  label: Option[String],
  priority: Priority,
  query: OdinsonQuery,
)


class RuleReader(val compiler: QueryCompiler) {

  /** gets a rule stream and returns a sequence of extractors ready to be used */
  def compileRuleStream(input: SituatedStream): Seq[Extractor] = {
    compileRuleStream(input, Map.empty[String, String])
  }

  /** Gets a rule stream as well as a map of variables.
    *  Returns a sequence of extractors ready to be used.
    *  The variables passed as an argument will override the variables declared in the file.
    */
  def compileRuleStream(input: SituatedStream, variables: Map[String, String]): Seq[Extractor] = {
    val ruleFiles = parseRuleFile(input, variables)
    mkExtractorsFromRuleFiles(ruleFiles, variables)
  }

  /**
    * gets the path to a rule file and returns a sequence of extractors ready to be used
    * @param input String path to the rule file
    * @return extractors
    */
  def compileRuleFile(input: String): Seq[Extractor] = {
    compileRuleFile(input, Map.empty[String, String])
  }

  /** Gets the path to a rule file as well as a map of variables.
   *  Returns a sequence of extractors ready to be used.
   *  The variables passed as an argument will override the variables declared in the file.
   */
  def compileRuleFile(input: String, variables: Map[String, String]): Seq[Extractor] = {
    compileRuleFile(new File(input), variables)
  }

  /** gets a rule File object and returns a sequence of extractors ready to be used */
  def compileRuleFile(input: File): Seq[Extractor] = {
    compileRuleFile(input, Map.empty[String, String])
  }

  /** Gets a rule File object as well as a map of variables.
    *  Returns a sequence of extractors ready to be used.
    *  The variables passed as an argument will override the variables declared in the file.
    */
  def compileRuleFile(input: File, variables: Map[String, String]): Seq[Extractor] = {
    compileRuleStream(SituatedStream.fromFile(input.getCanonicalPath), variables)
  }

  /**
    * Gets the path to a rule file in the jar resources as well as a map of variables.
    * Returns a sequence of extractors ready to be used
    */
  def compileRuleResource(rulePath: String): Seq[Extractor] = {
    compileRuleResource(rulePath, Map.empty[String, String])
  }

  /**
    * Gets the path to a rule file in the jar resources as well as a map of variables.
    * Returns a sequence of extractors ready to be used
    */
  def compileRuleResource(rulePath: String, variables: Map[String, String]): Seq[Extractor] = {
    compileRuleStream(SituatedStream.fromResource(rulePath), variables)
  }

  /**
    * Gets the actual rules content as a string.
    * Returns a sequence of extractors ready to be used
    */
  def compileRuleString(rules: String): Seq[Extractor] = {
    compileRuleString(rules, Map.empty[String, String])
  }

  /**
    * Gets the actual rules content as a string.
    * Returns a sequence of extractors ready to be used
    */
  def compileRuleString(rules: String, variables: Map[String, String]): Seq[Extractor] = {
    compileRuleStream(SituatedStream.fromString(rules), variables)
  }

  /** Parses the content of the rule file and returns a RuleFile object
   *  that contains the parsed rules and the variables declared in the file.
   *  Note that variable replacement hasn't happened yet.
   */
  def parseRuleFile(input: SituatedStream, parentVars: Map[String, String]): Seq[RuleFile] = {
    val yaml = new Yaml(new Constructor(classOf[JMap[String, Any]]))
    val master = using(input.stream) { stream =>
      println(input.canonicalPath)
      yaml.load(stream).asInstanceOf[JMap[String, Any]].asScala.toMap
    }
    val localVariables = mkVariables(master) ++ parentVars
    mkRules(master, input, localVariables)
  }

  def mkExtractorsFromRuleFiles(rfs: Seq[RuleFile], variables: Map[String, String]): Seq[Extractor] = {
    rfs.flatMap(r => mkExtractors(r, variables))
  }

  /** gets a RuleFile and returns a sequence of extractors */
  def mkExtractors(f: RuleFile): Seq[Extractor] = {
    mkExtractors(f.rules, f.variables)
  }

  /** Gets a RuleFile and a variable map and returns a sequence of extractors.
   *  Variables in RuleFile are overridden by the ones provided as argument to this function.
   */
  def mkExtractors(f: RuleFile, variables: Map[String, String]): Seq[Extractor] = {
    // The order in which the variable maps are concatenated is important.
    // The variables provided should override the variables in the RuleFile.
    mkExtractors(f.rules, f.variables ++ variables)
  }

  /** gets a sequence of rules and returns a sequence of extractors */
  def mkExtractors(rules: Seq[Rule]): Seq[Extractor] = {
    mkExtractors(rules, Map.empty[String, String])
  }

  /** Gets a sequence of rules as well as a variable map
   *  and returns a sequence of extractors ready to be used.
   */
  def mkExtractors(rules: Seq[Rule], variables: Map[String, String]): Seq[Extractor] = {
    val varsub = new VariableSubstitutor(variables)
    for (rule <- rules) yield mkExtractor(rule, varsub)
  }

  private def mkExtractor(rule: Rule, varsub: VariableSubstitutor): Extractor = {
    // any field in the rule may contain variables,
    // so we need to pass them through the variable substitutor
    val name = varsub(rule.name)
    val label = rule.label.map(varsub.apply)
    val ruleType = varsub(rule.ruletype)
    val priority = Priority(varsub(rule.priority))
    val pattern = varsub(rule.pattern)
    // compile query
    val query = ruleType match {
      case "basic" => compiler.compile(pattern)
      case "event" => compiler.compileEventQuery(pattern)
      case t => sys.error(s"invalid rule type '$t'")
    }
    // return an extractor
    Extractor(name, label, priority, query)
  }

  private def mkVariables(data: Map[String, Any]): Map[String, String] = {
    data.get("vars").map(parseVariables).getOrElse(Map.empty)
  }

  private def parseVariables(data: Any): Map[String, String] = {
    data match {
      case vars: JMap[_, _] =>
        vars
          .asScala
          .map { case (k, v) => k.toString -> processVar(v) }
          .toMap
      case _ => sys.error(s"invalid variables data: ${data}")
    }
  }

  // Variables can be a string, or optionally a list of strings which are combined with OR.
  // This is largely to support clean diffs when changes are made to variables, e.g., triggers.
  private def processVar(varValue: Any): String = {
    varValue match {
      // If the variable is a string, clean the whitespace and return
      case s: String => s
      // If the variable is a number, convert it into a string
      case n: Integer => n.toString
      // Else, if it's a list:
      case arr:java.util.ArrayList[_] => arr.asScala
        .map(_.toString.trim)
        .mkString("|")  // concatenate with OR
      case _ => ???
    }
  }

  private def mkRules(data: Map[String, Any], source: SituatedStream, vars: Map[String, String]): Seq[RuleFile] = {
    data.get("rules") match {
      case None => Seq.empty
      case Some(rules: Collection[_]) =>
        rules.asScala.toSeq.flatMap { r =>
          makeOrImportRules(r, source, vars)
        }
      case _ => sys.error("invalid rules data")
    }
  }


  def makeOrImportRules(data: Any, source: SituatedStream, parentVars: Map[String, String]): Seq[RuleFile] = {
    data match {
      case imported: JMap[String, Any] if imported.asScala.contains("import") =>
        assert(source.from != RuleSources.string, "Imports are not supported for string-only rules")
        // import rules from a file and return them
        val importVars = mkVariables(imported.asScala.toMap)
        importRules(imported.asScala.toMap, source, parentVars ++ importVars)
        // RuleFile (seq[Rule], vars)
      case _ => Seq(RuleFile(Seq(mkRule(data)), parentVars))
    }
  }

  private def importRules(
    data: Map[String, Any],
    source: SituatedStream,
    importVars: Map[String, String],
  ): Seq[RuleFile] = {
    // get the current working directory, with ending separator
    val relativePath = data("import").toString
    // handle substitutions in path name
    val resolved = new VariableSubstitutor(importVars).apply(relativePath)
    val importStream = source.relativePathStream(resolved)
    parseRuleFile(importStream, importVars)
  }

  private def mkRule(data: Any): Rule = {
    data match {
      case data: JMap[_, _] =>
        val fields = data.asInstanceOf[JMap[String, Any]].asScala.toMap
        // helper function to retrieve an optional field
        def getField(name: String) =
          fields.get(name).map(_.toString)
        // helper function to retrieve a required field
        def getRequiredField(name: String) =
          getField(name).getOrElse(sys.error(s"'$name' is required"))
        // read fields
        val name = getRequiredField("name")
        val label = getField("label")
        val ruleType = getRequiredField("type")
        val priority = fields.getOrElse("priority", "1").toString
        val pattern = getRequiredField("pattern")
        // return rule
        Rule(name, label, ruleType, priority, pattern)
      case _ => sys.error("invalid rule data")
    }
  }

}
