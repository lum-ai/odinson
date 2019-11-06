import ReleaseTransformations._

organization in ThisBuild := "ai.lum"

scalaVersion in ThisBuild := "2.12.10"

lazy val commonSettings = Seq(
  // show test duration
  testOptions in Test += Tests.Argument("-oD"),
  excludeDependencies += "commons-logging" % "commons-logging"
)

lazy val core = project
  .enablePlugins(BuildInfoPlugin)
  .settings(commonSettings)
  .settings(
    buildInfoPackage := "ai.lum.odinson",
    buildInfoOptions += BuildInfoOption.ToJson,
    buildInfoKeys := Seq[BuildInfoKey](
      name, version, scalaVersion, sbtVersion, libraryDependencies, scalacOptions,
      "builtAt" -> {
        val date = new java.util.Date
        val formatter = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
        formatter.format(date)
      },
      "gitCurrentBranch" -> { git.gitCurrentBranch.value },
      "gitHeadCommit" -> { git.gitHeadCommit.value.getOrElse("") },
      "gitHeadCommitDate" -> { git.gitHeadCommitDate.value.getOrElse("") },
      "gitUncommittedChanges" -> { git.gitUncommittedChanges.value }
    )
  )

lazy val extra = project
  .aggregate(core)
  .dependsOn(core)
  .settings(commonSettings)


lazy val backend = project
  .enablePlugins(PlayScala)
  .aggregate(core)
  .dependsOn(core % "test->test;compile->compile")
  .dependsOn(extra)
  .settings(commonSettings)
  .settings(
    // Dev settings which are read prior to loading of config.
    // See https://www.playframework.com/documentation/2.7.x/ConfigFile#Using-with-the-run-command
    PlayKeys.devSettings += "play.server.http.port" -> "9000",
    PlayKeys.devSettings += "play.server.http.address" -> "0.0.0.0",
    PlayKeys.devSettings += "play.server.http.idleTimeout" -> "infinite"
  )


// release steps
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("+publishSigned"),
  releaseStepCommandAndRemaining("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)


// Publishing settings

publishTo in ThisBuild := sonatypePublishToBundle.value

publishMavenStyle in ThisBuild := true

publishArtifact in Test := false

licenses in ThisBuild := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

homepage in ThisBuild := Some(url("https://github.com/lum-ai/odinson"))

scmInfo in ThisBuild := Some(
  ScmInfo(
    url("https://github.com/lum-ai/odinson"),
    "scm:git@github.com:lum-ai/odinson.git"
  )
)

developers in ThisBuild := List(
  Developer(id="marcovzla", name="Marco Antonio Valenzuela Escárcega", email="marco@lum.ai", url=url("https://lum.ai"))
)
