name := "odinson-extra"

libraryDependencies ++= {

  val procVersion = "8.2.3"

  Seq(
    "org.scalatest" %% "scalatest" % "3.0.5" % "test",
    "ai.lum"     %% "nxmlreader"            % "0.1.2",
    "org.clulab" %% "processors-main"       % procVersion,
    "org.clulab" %% "processors-corenlp"       % procVersion,
  )

}
