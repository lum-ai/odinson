name := "odinson-extra"

resolvers += ("Artifactory" at "http://artifactory.cs.arizona.edu:8081/artifactory/sbt-release").withAllowInsecureProtocol(true)

libraryDependencies ++= {

  val procVersion = "8.4.6"

  Seq(
    "org.scalatest" %% "scalatest" % "3.0.5" % "test",
    "ai.lum"     %% "nxmlreader"            % "0.1.2",
    "org.clulab" %% "processors-main"       % procVersion,
    "org.clulab" %% "processors-corenlp"    % procVersion,
    "org.clulab" %% "reach-processors"      % "1.6.3-SNAPSHOT"
  )

}
