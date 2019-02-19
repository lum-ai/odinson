name := "odinson-extra"

libraryDependencies ++= {

  val procVersion = "7.4.4"

  Seq(
    "ai.lum"     %% "nxmlreader"            % "0.1.2",
    "org.clulab" %% "processors-main"       % procVersion,
    "org.clulab" %% "processors-modelsmain" % procVersion
  )

}
