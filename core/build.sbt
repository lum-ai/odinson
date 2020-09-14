name := "odinson-core"

libraryDependencies ++= {

  val luceneVersion = "6.6.6"

  Seq(
    "org.scalatest" %% "scalatest" % "3.0.5" % "test",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "ch.qos.logback" %  "logback-classic" % "1.1.10",
    "ai.lum" %% "common" % "0.1.5",
    "org.apache.lucene" % "lucene-core" % luceneVersion,
    "org.apache.lucene" % "lucene-queryparser" % luceneVersion,
    "org.apache.lucene" % "lucene-highlighter" % luceneVersion,
    "com.zaxxer" % "HikariCP" % "3.3.1",
    "com.h2database" % "h2" % "1.4.198",
    "com.lihaoyi" %% "upickle" % "0.7.5",
    "com.lihaoyi" %% "fastparse" % "2.1.0",
    "org.yaml" % "snakeyaml" % "1.25",
  )

}
