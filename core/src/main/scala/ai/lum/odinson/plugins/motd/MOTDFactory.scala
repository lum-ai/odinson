package ai.lum.odinson.plugins.motd

import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._

object MOTDFactory {
  val config = ConfigFactory.load()

  def get(): MOTD = {
    val motds = config
        .apply[List[String]]("odinson.plugins.motd.providers")
        .flatMap { name =>
          Option {
            try {
              Class.forName(name)
            }
            catch {
              case _: Throwable => null
            }
          }.map(_.newInstance.asInstanceOf[MOTD])
        }

    new MultiMOTD(new MockMOTD +: motds)
  }
}
