name := "odinson-extra"

libraryDependencies ++= {

  val procVersion = "7.4.4"

  Seq(
    "org.scalatest" %% "scalatest" % "3.0.5" % "test",
    "ai.lum"     %% "nxmlreader"            % "0.1.2",
    "org.clulab" %% "processors-main"       % procVersion,
    "org.clulab" %% "processors-modelsmain" % procVersion,
    "org.clulab" %% "processors-corenlp"       % procVersion,
    "org.clulab" %% "processors-modelscorenlp" % procVersion,
  )

}
