package ai.lum.odinson.plugins.motd

import java.io.PrintStream

class MultiMOTD(motds: Seq[MOTD]) extends MOTD {

  def show(printWriter: PrintStream): Unit = {
    motds.foreach { motd =>
      motd.show(printWriter)
    }
  }
}
