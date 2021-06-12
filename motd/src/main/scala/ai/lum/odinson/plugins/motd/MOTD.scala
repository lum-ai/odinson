package ai.lum.odinson.plugins.motd

import java.io.PrintStream

trait MOTD {
  def show(printWriter: PrintStream): Unit
}
