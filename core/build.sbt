name := "odinson-core"

libraryDependencies ++= {

  val luceneVersion = "6.6.6"

  Seq(
    "org.scalatest" %% "scalatest" % "3.0.5",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2",
    "ch.qos.logback" %  "logback-classic" % "1.2.3",
    "ai.lum" %% "common" % "0.1.5",
    "org.apache.lucene" % "lucene-core" % luceneVersion,
    "org.apache.lucene" % "lucene-queryparser" % luceneVersion,
    "org.apache.lucene" % "lucene-highlighter" % luceneVersion,
    // used for metadata queries
    "org.apache.lucene" % "lucene-join" % luceneVersion,
    "com.zaxxer" % "HikariCP" % "3.3.1",
    "com.h2database" % "h2" % "1.4.198",
    "com.lihaoyi" %% "upickle" % "0.7.5",
    "com.lihaoyi" %% "fastparse" % "2.1.0",
    "org.yaml" % "snakeyaml" % "1.25",
  )

}
