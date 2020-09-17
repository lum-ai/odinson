package ai.lum.odinson.state

import com.typesafe.config.Config
import ai.lum.common.ConfigUtils._
import ai.lum.common.TryWithResources.using
import ai.lum.odinson.utils.exceptions.OdinsonException

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
      case "mock" => MockStateFactory(config)
      case _ => throw new OdinsonException(s"Unknown state provider: $provider")
    }

    stateFactory
  }
}
