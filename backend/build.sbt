name := "odinson-rest-api"

libraryDependencies ++= {
  Seq(
    guice,
    jdbc,
    ehcache,
    ws,
    "org.scalatestplus.play"  %% "scalatestplus-play" % "3.0.0" % Test
  )
}
