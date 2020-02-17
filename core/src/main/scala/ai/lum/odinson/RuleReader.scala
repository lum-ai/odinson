package ai.lum.odinson

import java.util.{ Collection, Map => JMap }
import scala.collection.JavaConverters._
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import ai.lum.odinson.compiler.QueryCompiler
import ai.lum.odinson.lucene.search.OdinsonQuery
import ai.lum.odinson.utils.VariableSubstitutor

case class Rule(
  name: String,
  ruletype: String,
  pattern: String,
)

case class Extractor(
  name: String,
  // label
  // priority
  query: OdinsonQuery,
)

case class RuleFile(
  rules: Seq[Rule],
  variables: Map[String, String],
)

case class Mention(
  odinsonMatch: OdinsonMatch,
  // label
  luceneDocId: Int,
  docId: String,
  sentenceId: String,
  foundBy: String,
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

  def mkExtractors(f: RuleFile): Seq[Extractor] = {
    mkExtractors(f.rules, f.variables)
  }

  def mkExtractors(f: RuleFile, variables: Map[String, String]): Seq[Extractor] = {
    // The order in which the variable maps are concatenated is important.
    // The variables provided should override the variables in the RuleFile.
    mkExtractors(f.rules, f.variables ++ variables)
  }

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
    val query = varsub(rule.ruletype) match {
      // TODO choose names
      case "basic" => compiler.compile(varsub(rule.pattern))
      case "event" => compiler.compileEventQuery(varsub(rule.pattern))
      case t => sys.error(s"invalid rule type '$t'")
    }
    Extractor(rule.name, query)
  }

  private def mkVariables(data: Map[String, Any]): Map[String, String] = {
    data.get("vars").map(parseVariables).getOrElse(Map.empty)
  }

  private def parseVariables(data: Any): Map[String, String] = {
    data match {
      case vars: JMap[_, _] =>
        vars
          .asScala
          .map { case (k, v) => k.toString -> v.toString }
          .toMap
      case _ => sys.error("invalid variables data")
    }
  }

  private def mkRules(data: Any): Seq[Rule] = {
    data match {
      case data: JMap[_, _] =>
        data.get("rules") match {
          case None => Seq.empty
          case Some(rules: Collection[_]) =>
            rules.asScala.map(mkRule).toSeq
          case _ => sys.error("invalid rules data")
        }
      case _ => sys.error("invalid rules data")
    }
  }

  private def mkRule(data: Any): Rule = {
    data match {
      case data: JMap[_, _] =>
        val fields = data.asInstanceOf[JMap[String, Any]].asScala.toMap
        def getField(name: String) = fields.get(name).getOrElse(sys.error(s"'$name' is required")).toString()
        val name = getField("name")
        val ruletype = getField("type")
        val pattern = getField("pattern")
        Rule(name, ruletype, pattern)
      case _ => sys.error("invalid rule data")
    }
  }

}
