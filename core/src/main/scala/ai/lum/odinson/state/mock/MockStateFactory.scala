package ai.lum.odinson.state.mock

import ai.lum.odinson.state.State
import ai.lum.odinson.state.StateFactory

import com.typesafe.config.Config

class MockStateFactory extends StateFactory {

  override def usingState[T](function: State => T): T = {
    function(MockState)
  }
}

object MockStateFactory {

  def apply(config: Config): MockStateFactory = {
    new MockStateFactory()
  }
}