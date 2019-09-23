name := "odinson-core"

libraryDependencies ++= {

  val luceneVersion = "6.6.6"

  Seq(
    "org.scalatest" %% "scalatest" % "3.0.5" % "test",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "ch.qos.logback" %  "logback-classic" % "1.1.10",
    "ai.lum" %% "common" % "0.0.9-SNAPSHOT",
    "org.apache.lucene" % "lucene-core" % luceneVersion,
    "org.apache.lucene" % "lucene-queryparser" % luceneVersion,
    "org.apache.lucene" % "lucene-highlighter" % luceneVersion,
    "com.ibm.icu" % "icu4j" % "54.1",
    "com.zaxxer" % "HikariCP" % "3.3.1",
    "com.h2database" % "h2" % "1.4.198",
    "com.lihaoyi" %% "upickle" % "0.7.5",
    "com.lihaoyi" %% "fastparse" % "2.1.0",
    "com.twitter" %% "chill" % "0.9.3"
  )

}
