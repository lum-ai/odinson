package ai.lum.odinson.apps

import ai.lum.odinson.plugins.motd.MOTDFactory

object MOTDApp extends App {
  MOTDFactory.get.show(System.out)
}
