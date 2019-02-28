package ai.lum.odinson.state

import org.h2.jdbcx.JdbcDataSource

class State {

  val dataSource = new JdbcDataSource()
  dataSource.setURL("jdbc:h2:mem:odinson")
  val connection = dataSource.getConnection()

}
