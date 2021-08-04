package ai.lum.odinson

import java.io.File
import java.util.{ Collection, Map => JMap }

import ai.lum.common.TryWithResources.using

import scala.collection.JavaConverters._
import org.apache.lucene.search.{ Query => LuceneQuery }
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import ai.lum.odinson.compiler.QueryCompiler
import ai.lum.odinson.lucene.search.OdinsonQuery
import ai.lum.odinson.metadata.MetadataCompiler
import ai.lum.odinson.utils.exceptions.OdinsonException
import ai.lum.odinson.utils.{ RuleSources, SituatedStream, VariableSubstitutor }

/** A RuleFile is the result of parsing a yaml file.
  *  At this point variables haven't been replaced
  *  and patterns haven't been compiled
  */
case class RuleFile(
  rules: Seq[Rule],
  variables: Map[String, String],
  metadataFilter: Option[LuceneQuery]
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
  pattern: String
)

/** An Extractor is a compiled Rule.
  *  It is ready to be executed by the ExtractionEngine.
  */
case class Extractor(
  name: String,
  label: Option[String],
  priority: Priority,
  query: OdinsonQuery
)

class RuleReader(val compiler: QueryCompiler) {

  /** gets a rule stream and returns a sequence of extractors ready to be used */
  def compileRuleStream(input: SituatedStream): Seq[Extractor] = {
    compileRuleStream(input, Map.empty[String, String], None)
  }

  /** Gets a rule stream as well as a map of variables.
    *  Returns a sequence of extractors ready to be used.
    *  The variables passed as an argument will override the variables declared in the file.
    */
  def compileRuleStream(input: SituatedStream, variables: Map[String, String]): Seq[Extractor] = {
    compileRuleStream(input, variables, None)
  }

  /** Gets a rule stream as well as a metadata filter.
    *  Returns a sequence of extractors ready to be used.
    *  The variables passed as an argument will override the variables declared in the file.
    */
  def compileRuleStream(input: SituatedStream, metadataFilter: LuceneQuery): Seq[Extractor] = {
    compileRuleStream(input, Map.empty, Some(metadataFilter))
  }

  /** Gets a rule stream, variables, and a metadata filter.
    *  Returns a sequence of extractors ready to be used.
    *  The variables passed as an argument will override the variables declared in the file.
    */
  def compileRuleStream(
    input: SituatedStream,
    variables: Map[String, String],
    metadataFilterOpt: Option[LuceneQuery]
  ): Seq[Extractor] = {
    val ruleFiles = parseRuleFile(input, variables, metadataFilterOpt)
    mkExtractorsFromRuleFiles(ruleFiles, variables)
  }

  /** gets the path to a rule file and returns a sequence of extractors ready to be used
    * @param input String path to the rule file
    * @return extractors
    */
  def compileRuleFile(input: String): Seq[Extractor] = {
    compileRuleFile(input, Map.empty[String, String], None)
  }

  /** Gets the path to a rule file as well as a map of variables.
    *  Returns a sequence of extractors ready to be used.
    *  The variables passed as an argument will override the variables declared in the file.
    */
  def compileRuleFile(input: String, variables: Map[String, String]): Seq[Extractor] = {
    compileRuleFile(new File(input), variables, None)
  }

  /** Gets the path to a rule file as well as a metadata filter.
    *  Returns a sequence of extractors ready to be used.
    *  The variables passed as an argument will override the variables declared in the file.
    */
  def compileRuleFile(input: String, metadataFilter: LuceneQuery): Seq[Extractor] = {
    compileRuleFile(new File(input), Map.empty[String, String], Some(metadataFilter))
  }

  /** Gets the path to a rule file, a map of variables, and a metadata filter.
    *  Returns a sequence of extractors ready to be used.
    *  The variables passed as an argument will override the variables declared in the file.
    */
  def compileRuleFile(
    input: String,
    variables: Map[String, String],
    metadataFilterOpt: Option[LuceneQuery]
  ): Seq[Extractor] = {
    compileRuleFile(new File(input), variables, metadataFilterOpt)
  }

  /** gets a rule File object and returns a sequence of extractors ready to be used */
  def compileRuleFile(input: File): Seq[Extractor] = {
    compileRuleFile(input, Map.empty[String, String], None)
  }

  /** Gets a rule File object and a map of variables.
    *  Returns a sequence of extractors ready to be used.
    *  The variables passed as an argument will override the variables declared in the file.
    */
  def compileRuleFile(input: File, variables: Map[String, String]): Seq[Extractor] = {
    compileRuleStream(SituatedStream.fromFile(input.getCanonicalPath), variables, None)
  }

  /** Gets a rule File object and a metadata filter.
    *  Returns a sequence of extractors ready to be used.
    *  The variables passed as an argument will override the variables declared in the file.
    */
  def compileRuleFile(input: File, metadataFilterOpt: LuceneQuery): Seq[Extractor] = {
    compileRuleStream(
      SituatedStream.fromFile(input.getCanonicalPath),
      Map.empty,
      Some(metadataFilterOpt)
    )
  }

  /** Gets a rule File object, a map of variables, and a metadata filter.
    *  Returns a sequence of extractors ready to be used.
    *  The variables passed as an argument will override the variables declared in the file.
    */
  def compileRuleFile(
    input: File,
    variables: Map[String, String],
    metadataFilterOpt: Option[LuceneQuery]
  ): Seq[Extractor] = {
    compileRuleStream(SituatedStream.fromFile(input.getCanonicalPath), variables, metadataFilterOpt)
  }

  /** Gets the path to a rule file in the jar resources as well as a map of variables.
    * Returns a sequence of extractors ready to be used
    */
  def compileRuleResource(rulePath: String): Seq[Extractor] = {
    compileRuleResource(rulePath, Map.empty[String, String], None)
  }

  /** Gets the path to a rule file in the jar resources and a map of variables.
    * Returns a sequence of extractors ready to be used
    */
  def compileRuleResource(rulePath: String, variables: Map[String, String]): Seq[Extractor] = {
    compileRuleStream(SituatedStream.fromResource(rulePath), variables, None)
  }

  /** Gets the path to a rule file in the jar resources and a metadata filter.
    * Returns a sequence of extractors ready to be used
    */
  def compileRuleResource(rulePath: String, metadataFilter: LuceneQuery): Seq[Extractor] = {
    compileRuleStream(SituatedStream.fromResource(rulePath), Map.empty, Some(metadataFilter))
  }

  /** Gets the path to a rule file in the jar resources, a map of variables, and a metadata filter.
    * Returns a sequence of extractors ready to be used
    */
  def compileRuleResource(
    rulePath: String,
    variables: Map[String, String],
    metadataFilterOpt: Option[LuceneQuery]
  ): Seq[Extractor] = {
    compileRuleStream(SituatedStream.fromResource(rulePath), variables, metadataFilterOpt)
  }

  /** Gets the actual rules content as a string.
    * Returns a sequence of extractors ready to be used
    */
  def compileRuleString(rules: String): Seq[Extractor] = {
    compileRuleString(rules, Map.empty[String, String], None)
  }

  /** Gets the actual rules content as a string.
    * Returns a sequence of extractors ready to be used
    */
  def compileRuleString(rules: String, variables: Map[String, String]): Seq[Extractor] = {
    compileRuleStream(SituatedStream.fromString(rules), variables, None)
  }

  /** Gets the actual rules content as a string.
    * Returns a sequence of extractors ready to be used
    */
  def compileRuleString(rules: String, metadataFilter: LuceneQuery): Seq[Extractor] = {
    compileRuleStream(SituatedStream.fromString(rules), Map.empty, Some(metadataFilter))
  }

  /** Gets the actual rules content as a string.
    * Returns a sequence of extractors ready to be used
    */
  def compileRuleString(
    rules: String,
    variables: Map[String, String],
    metadataFilterOpt: Option[LuceneQuery]
  ): Seq[Extractor] = {
    compileRuleStream(SituatedStream.fromString(rules), variables, metadataFilterOpt)
  }

  /** Parses the content of the rule file and returns a RuleFile object
    *  that contains the parsed rules and the variables declared in the file.
    *  Note that variable replacement hasn't happened yet.
    */
  def parseRuleFile(
    input: SituatedStream,
    parentVars: Map[String, String],
    metadataFilterOptIn: Option[LuceneQuery]
  ): Seq[RuleFile] = {
    val master = yamlContents(input)
    // Parent vars passed in case we need to resolve variables in import paths
    val localVariables = mkVariables(master, input, parentVars) ++ parentVars
    val metadataFilter = mkMetadataFilter(master, metadataFilterOptIn)
    mkRules(master, input, localVariables, metadataFilter)
  }

  def mkExtractorsFromRuleFiles(
    rfs: Seq[RuleFile],
    variables: Map[String, String]
  ): Seq[Extractor] = {
    rfs.flatMap(r => mkExtractors(r, variables))
  }

  /** gets a RuleFile and returns a sequence of extractors */
  def mkExtractors(f: RuleFile): Seq[Extractor] = {
    mkExtractors(f.rules, f.variables, f.metadataFilter)
  }

  /** Gets a RuleFile and a variable map and returns a sequence of extractors.
    *  Variables in RuleFile are overridden by the ones provided as argument to this function.
    */
  def mkExtractors(f: RuleFile, variables: Map[String, String]): Seq[Extractor] = {
    // The order in which the variable maps are concatenated is important.
    // The variables provided should override the variables in the RuleFile.
    mkExtractors(f.rules, f.variables ++ variables, f.metadataFilter)
  }

  /** gets a sequence of rules and returns a sequence of extractors */
  def mkExtractors(rules: Seq[Rule]): Seq[Extractor] = {
    mkExtractors(rules, Map.empty[String, String], None)
  }

  /** Gets a sequence of rules as well as a variable map
    *  and returns a sequence of extractors ready to be used.
    */
  def mkExtractors(
    rules: Seq[Rule],
    variables: Map[String, String],
    metadataFilterOpt: Option[LuceneQuery]
  ): Seq[Extractor] = {
    val varsub = new VariableSubstitutor(variables)
    for (rule <- rules) yield mkExtractor(rule, varsub, metadataFilterOpt)
  }

  private def mkExtractor(
    rule: Rule,
    varsub: VariableSubstitutor,
    metadataFilterOpt: Option[LuceneQuery]
  ): Extractor = {
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
      case t       => throw new OdinsonException(s"invalid rule type '$t'")
    }
    // add the metadata filter if applicable, and return an extractor
    if (metadataFilterOpt.isEmpty) {
      Extractor(name, label, priority, query)
    } else {
      Extractor(name, label, priority, compiler.mkQuery(query, metadataFilterOpt.get))
    }
  }

  private def mkMetadataFilter(
    data: Map[String, Any],
    parentMetadataFilter: Option[LuceneQuery]
  ): Option[LuceneQuery] = {
    val localFilter = data.get("metadataFilters").flatMap(parseFilter)
    joinFilters(localFilter, parentMetadataFilter)
  }

  private def parseFilter(data: Any): Option[LuceneQuery] = {
    data match {
      case pattern: String => Some(MetadataCompiler.mkQuery(pattern))
      case filters: Collection[_] =>
        val allFilters = filters.asScala.toSeq.flatMap(parseFilter)
        joinFilters(allFilters)
      case _ => ???
    }
  }

  def joinFilters(q1: Option[LuceneQuery], q2: Option[LuceneQuery]): Option[LuceneQuery] = {
    if (q1.isEmpty) q2
    else if (q2.isEmpty) q1
    else {
      // both defined
      joinFilters(Seq(q1.get, q2.get))
    }
  }

  def joinFilters(queries: Seq[LuceneQuery]): Option[LuceneQuery] = {
    queries match {
      case Seq()         => None
      case Seq(oneQuery) => Some(oneQuery)
      case several       => Some(MetadataCompiler.combineAnd(several))
    }
  }

  // parentVars passed in case we need to resolve variables in import paths
  private def mkVariables(
    data: Map[String, Any],
    source: SituatedStream,
    parentVars: Map[String, String]
  ): Map[String, String] = {
    data.get("vars").map(parseVariables(_, source, parentVars)).getOrElse(Map.empty)
  }

  // Parent vars passed in case we need to resolve variables in import paths
  private def parseVariables(
    data: Any,
    source: SituatedStream,
    parentVars: Map[String, String]
  ): Map[String, String] = {
    data match {
      // if the vars are given as an import from a file
      case relativePath: String =>
        verifyImport(source)
        // handle substitutions in path name
        val resolved = new VariableSubstitutor(parentVars).apply(relativePath)
        val importStream = source.relativePathStream(resolved)
        importVars(importStream)
      // if the vars are given directly in the current yaml
      case vars: JMap[_, _] =>
        vars
          .asScala
          .map { case (k, v) => k.toString -> processVar(v) }
          .toMap
      case _ => throw new OdinsonException(s"invalid variables data: ${data}")
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
      case arr: java.util.ArrayList[_] => arr.asScala
          .map(_.toString.trim)
          .mkString("|") // concatenate with OR
      case _ => ???
    }
  }

  private def importVars(input: SituatedStream): Map[String, String] = {
    val contents = yamlContents(input)
    contents.mapValues(processVar)
  }

  private def mkRules(
    data: Map[String, Any],
    source: SituatedStream,
    vars: Map[String, String],
    metadataFilterOpt: Option[LuceneQuery]
  ): Seq[RuleFile] = {
    data.get("rules") match {
      case None => Seq.empty
      case Some(rules: Collection[_]) =>
        rules.asScala.toSeq.flatMap { r =>
          makeOrImportRules(r, source, vars, metadataFilterOpt)
        }
      case _ => throw new OdinsonException("invalid rules data")
    }
  }

  def makeOrImportRules(
    data: Any,
    source: SituatedStream,
    parentVars: Map[String, String],
    metadataFilterOpt: Option[LuceneQuery]
  ): Seq[RuleFile] = {
    data match {
      case ruleJMap: JMap[_, _] =>
        val rulesData = ruleJMap.asInstanceOf[JMap[String, Any]].asScala.toMap
        // If the rules are imported:
        if (rulesData.contains("import")) {
          verifyImport(source)
          // import rules from a file and return them
          // Parent vars passed in case we need to resolve variables in import paths
          val importVars = mkVariables(rulesData, source, parentVars)
          // resolve the metadata filters, combining with AND
          val importFilters = mkMetadataFilter(rulesData, metadataFilterOpt)
          importRules(rulesData, source, parentVars ++ importVars, importFilters)
        } else {
          // Otherwise, process the data as individual rules
          Seq(RuleFile(Seq(mkRule(data)), parentVars, metadataFilterOpt))
        }
      case _ => ???
    }
  }

  private def importRules(
    data: Map[String, Any],
    source: SituatedStream,
    importVars: Map[String, String],
    metadataFilterOpt: Option[LuceneQuery]
  ): Seq[RuleFile] = {
    // get the current working directory, with ending separator
    val relativePath = data("import").toString
    // handle substitutions in path name
    val resolved = new VariableSubstitutor(importVars).apply(relativePath)
    val importStream = source.relativePathStream(resolved)
    parseRuleFile(importStream, importVars, metadataFilterOpt)
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
          getField(name).getOrElse(throw new OdinsonException(s"'$name' is required"))
        // read fields
        val name = getRequiredField("name")
        val label = getField("label")
        val ruleType = getRequiredField("type")
        val priority = fields.getOrElse("priority", "1").toString
        val pattern = getRequiredField("pattern")
        // return rule
        Rule(name, label, ruleType, priority, pattern)
      case _ => throw new OdinsonException("invalid rule data")
    }
  }

  // -------------------------------------------------
  //                    HELPERS
  // -------------------------------------------------

  private def yamlContents(input: SituatedStream): Map[String, Any] = {
    val yaml = new Yaml(new Constructor(classOf[JMap[String, Any]]))
    using(input.stream) { stream =>
      yaml.load(stream).asInstanceOf[JMap[String, Any]].asScala.toMap
    }
  }

  private def verifyImport(source: SituatedStream): Unit = {
    if (source.from == RuleSources.string) {
      throw new OdinsonException("Imports are not supported for string-only rules")
    }
  }

}
