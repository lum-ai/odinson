package ai.lum.odinson.state

import java.util.concurrent.atomic.AtomicLong

import com.typesafe.config.Config
import ai.lum.common.ConfigUtils._

object StateFactory {
  protected var count: AtomicLong = new AtomicLong

  def newSqlState(config: Config, index: Long): State = {
    val jdbcUrl = config[String]("state.jdbc.url")
    val state = new SqlState(jdbcUrl, index)

    state
  }

  def newState(config: Config): State = {
    // TODO: Also add PID in case multiple Javas are running at the same time.
    val index = count.getAndIncrement
    val provider = config[String]("state.provider")
    val state = provider match {
      case "sql" => newSqlState(config, index)
      case "file" => new FileState()
      case "memory" => new MemoryState()
      case _ => throw new Exception(s"Unknown state provider: $provider")
    }

    state
  }
}
