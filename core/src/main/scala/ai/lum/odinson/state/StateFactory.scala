package ai.lum.odinson.state

import com.typesafe.config.Config
import ai.lum.common.ConfigUtils._
import ai.lum.common.TryWithResources.using

trait StateFactory {
  def usingState[T](function: State => T): T
}

object StateFactory {

  def apply(config: Config): StateFactory = {
    val provider = config[String]("state.provider")
    val stateFactory = provider match {
      case "sql" => SqlStateFactory(config)
      case "file" => FileStateFactory(config)
      case "memory" => MemoryStateFactory(config)
      case _ => throw new Exception(s"Unknown state provider: $provider")
    }

    stateFactory
  }
}
