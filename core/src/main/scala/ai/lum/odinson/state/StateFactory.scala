package ai.lum.odinson.state

import com.typesafe.config.Config

import ai.lum.common.ConfigUtils._

object StateFactory {

  def newState(config: Config): State = {
    val jdbcUrl = config[String]("state.jdbc.url")
    val state = new HikariState(jdbcUrl)

    state
  }
}
