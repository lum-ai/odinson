package ai.lum.odinson.utils

import scala.collection.JavaConverters._
import org.apache.commons.text.StringSubstitutor

class VariableSubstitutor(private var variables: Map[String, String]) {

  import VariableSubstitutor._

  def this() = this(Map.empty)

  private var substitutor = mkStringSubstitutor(variables)

  def setVariables(vars: Map[String, String]): VariableSubstitutor = {
    variables = vars
    substitutor = mkStringSubstitutor(variables)
    this
  }

  def addVariable(name: String, value: String): VariableSubstitutor = {
    variables += (name -> value)
    substitutor = mkStringSubstitutor(variables)
    this
  }

  def removeVariable(name: String): VariableSubstitutor = {
    variables -= name
    substitutor = mkStringSubstitutor(variables)
    this
  }

  def replace(source: String): String = {
    substitutor.replace(source)
  }

}

object VariableSubstitutor {

  private def mkStringSubstitutor(variables: Map[String, String]): StringSubstitutor = {
    new StringSubstitutor(variables.asJava)
      .setEnableSubstitutionInVariables(true)
  }

}
