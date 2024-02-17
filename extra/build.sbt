name := "odinson-extra"

resolvers ++= Seq(
  "clulab" at "https://artifactory.clulab.org/artifactory/sbt-release"
)

libraryDependencies ++= {

  val procVersion = "8.4.6"

  Seq(
    "org.scalatest" %% "scalatest" % "3.0.5" % "test",
    "ai.lum"     %% "nxmlreader"            % "0.1.2",
    "org.clulab" %% "processors-main"       % procVersion,
    "org.clulab" %% "processors-corenlp"    % procVersion,
  )

}
