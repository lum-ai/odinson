package ai.lum.odinson.state

import com.typesafe.config.Config
import ai.lum.common.ConfigUtils._
import ai.lum.common.TryWithResources.using
import ai.lum.odinson.state.fastsql.FastSqlStateFactory
import ai.lum.odinson.state.file.FileStateFactory
import ai.lum.odinson.state.memory.MemoryStateFactory
import ai.lum.odinson.state.sql.SqlStateFactory

trait StateFactory {
  def usingState[T](function: State => T): T
}

object StateFactory {

  def apply(config: Config): StateFactory = {
    val provider = config[String]("state.provider")
    val stateFactory = provider match {
      case "fastsql" => FastSqlStateFactory(config)
      case "file" => FileStateFactory(config)
      case "memory" => MemoryStateFactory(config)
      case "sql" => SqlStateFactory(config)
      case _ => throw new Exception(s"Unknown state provider: $provider")
    }

    stateFactory
  }
}
