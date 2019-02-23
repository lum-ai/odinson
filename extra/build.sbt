name := "odinson-extra"

libraryDependencies ++= {

  val procVersion = "7.4.4"

  Seq(
    "ai.lum"     %% "nxmlreader"            % "0.1.2",
    "ai.lum"     %% "labrador-core"         % "0.0.2-SNAPSHOT",
    "org.clulab" %% "processors-main"       % procVersion,
    "org.clulab" %% "processors-modelsmain" % procVersion
  )

}
