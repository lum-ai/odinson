package ai.lum.odinson.state

import com.typesafe.config.Config

import ai.lum.common.ConfigUtils._

object StateFactory {

  def newSqlState(config: Config): State = {
    val jdbcUrl = config[String]("state.jdbc.url")
    val state = new SqlState(jdbcUrl)

    state
  }

  def newState(config: Config): State = {
    val provider = config[String]("state.provider")
    val state = provider match {
      case "sql" => newSqlState(config)
      case "file" => new FileState()
      case "memory" => new MemoryState()
      case _ => throw new Exception(s"Unknown state provider: $provider")
    }

    state
  }
}
