package ai.lum.odinson.state.memory

import ai.lum.odinson.state.State
import ai.lum.odinson.state.StateFactory
import com.typesafe.config.Config

class MemoryStateFactory extends StateFactory {

  override def usingState[T](function: State => T): T = {
    function(new MemoryState())
  }
}

object MemoryStateFactory {

  def apply(config: Config): MemoryStateFactory = {
    new MemoryStateFactory()
  }
}