package ai.lum.odinson

import java.util.{ Collection, Map => JMap }
import scala.collection.JavaConverters._
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import ai.lum.odinson.compiler.QueryCompiler
import ai.lum.odinson.lucene.search.OdinsonQuery
import ai.lum.odinson.utils.VariableSubstitutor

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

  /** gets the contents of a rule file and returns a sequence of extractors ready to be used */
  def compileRuleFile(input: String): Seq[Extractor] = {
    compileRuleFile(input, Map.empty)
  }

  /** Gets the contents of a rule file as well as a map of variables.
   *  Returns a sequence of extractors ready to be used.
   *  The variables passed as an argument will override the variables declared in the file.
   */
  def compileRuleFile(input: String, variables: Map[String, String]): Seq[Extractor] = {
    mkExtractors(parseRuleFile(input), variables)
  }

  /** Parses the content of the rule file and returns a RuleFile object
   *  that contains the parsed rules and the variables declared in the file.
   *  Note that variable replacement hasn't happened yet.
   */
  def parseRuleFile(input: String): RuleFile = {
    val yaml = new Yaml(new Constructor(classOf[JMap[String, Any]]))
    val master = yaml.load(input).asInstanceOf[JMap[String, Any]].asScala.toMap
    val variables = mkVariables(master)
    val rules = mkRules(master)
    RuleFile(rules, variables)
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
    val ruletype = varsub(rule.ruletype)
    val priority = Priority(varsub(rule.priority))
    val pattern = varsub(rule.pattern)
    // compile query
    val query = ruletype match {
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
      case _ => sys.error("invalid variables data")
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

  private def mkRules(data: Map[String, Any]): Seq[Rule] = {
    data.get("rules") match {
      case None => Seq.empty
      case Some(rules: Collection[_]) => rules.asScala.map(mkRule).toSeq
      case _ => sys.error("invalid rules data")
    }
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
        val ruletype = getRequiredField("type")
        val priority = fields.getOrElse("priority", "1").toString
        val pattern = getRequiredField("pattern")
        // return rule
        Rule(name, label, ruletype, priority, pattern)
      case _ => sys.error("invalid rule data")
    }
  }

}
