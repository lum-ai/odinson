package ai.lum.odinson.state.fastsql

import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicLong

import ai.lum.common.ConfigUtils._
import ai.lum.common.TryWithResources.using
import ai.lum.odinson.state.State
import ai.lum.odinson.state.StateFactory
import com.typesafe.config.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

abstract class FastSqlStateFactory extends StateFactory

class DirectFastSqlStateFactory(jdbcUrl: String, index: Long, persist: Boolean) extends FastSqlStateFactory {
  protected var count: AtomicLong = new AtomicLong

  override def usingState[T](function: State => T): T = {
    using(DriverManager.getConnection(jdbcUrl)) { connection =>
      using(new FastSqlState(connection, index, count.getAndIncrement, persist)) { state =>
        function(state)
      }
    }
  }
}

class IndirectFastSqlStateFactory(dataSource: HikariDataSource, index: Long, persist: Boolean) extends FastSqlStateFactory {
  protected var count: AtomicLong = new AtomicLong

  override def usingState[T](function: State => T): T = {
    using(dataSource.getConnection) { connection: Connection =>
      using(new FastSqlState(connection, index, count.getAndIncrement, persist)) { state =>
        function(state)
      }
    }
  }
}

object FastSqlStateFactory {
  protected var count: AtomicLong = new AtomicLong

  def apply(config: Config): FastSqlStateFactory = {
    val jdbcUrl = config[String]("state.fastsql.url")
    val persist = config[Boolean]("state.fastsql.persist")
    val direct = config[Boolean]("state.fastsql.direct")

    if (direct)
      new DirectFastSqlStateFactory(jdbcUrl, count.getAndIncrement, persist)
    else {
      val dataSource: HikariDataSource = {
        val config = new HikariConfig
        config.setJdbcUrl(jdbcUrl)
        config.setPoolName("odinson")
        config.setUsername("")
        config.setPassword("")
        config.setMaximumPoolSize(10) // Don't do this?
        config.setMinimumIdle(2)
        config.setAutoCommit(false) // Try for faster.
        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "256")
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        new HikariDataSource(config)
      }

      new IndirectFastSqlStateFactory(dataSource, count.getAndIncrement, persist)
    }
  }
}