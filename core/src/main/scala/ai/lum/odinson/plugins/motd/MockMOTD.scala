package ai.lum.odinson.plugins.motd

import java.io.PrintStream

class MockMOTD extends MOTD {

  def show(printWriter: PrintStream): Unit = {
    printWriter.println(s"${this.getClass.getSimpleName} says hello.")
  }
}
