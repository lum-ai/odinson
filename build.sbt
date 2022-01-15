import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

organization in ThisBuild := "ai.lum"

scalaVersion in ThisBuild := "2.12.10"

fork in ThisBuild := true

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
      name,
      version,
      scalaVersion,
      sbtVersion,
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
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(generalDockerSettings)
  .settings(
    packageName in Docker := "odinson-extras",
    //mainClass in Compile := Some("ai.lum.odinson.extra.IndexDocuments"),
    //dockerRepository := Some("index.docker.io"),
    //dockerChmodType := DockerChmodType.UserGroupWriteExecute
    javaOptions in Universal ++= Seq(
      "-J-Xmx6G"
      )
    )

// Docker settings
val gitDockerTag = settingKey[String]("Git commit-based tag for docker")
ThisBuild / gitDockerTag := {
  val shortHash: String = git.gitHeadCommit.value.get.take(7)
  val uncommittedChanges: Boolean = (git.gitUncommittedChanges).value
  s"""${shortHash}${if (uncommittedChanges) "-DIRTY" else ""}"""
}

lazy val generalDockerSettings = {
  Seq(
    parallelExecution in ThisBuild := false,
    // see https://www.scala-sbt.org/sbt-native-packager/formats/docker.html
    daemonUserUid in Docker := None,
    daemonUser in Docker    := "root",
    dockerUsername := Some("lumai"),
    dockerAliases ++= Seq(
      dockerAlias.value.withTag(Option("latest")),
      dockerAlias.value.withTag(Option(gitDockerTag.value))
      ),
    maintainer in Docker := "Gus Hahn-Powell <ghp@lum.ai>",
    dockerBaseImage := "adoptopenjdk/openjdk11",
    javaOptions in Universal ++= Seq(
      "-Dodinson.dataDir=/app/data/odinson"
      )
    )
}

// Release steps
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
  Developer(
    id = "marcovzla",
    name = "Marco Antonio Valenzuela Escárcega",
    email = "marco@lum.ai",
    url = url("https://lum.ai")
    )
  )

// tasks
addCommandAlias("dockerize", ";clean;compile;test;docker:publishLocal")
addCommandAlias("dockerizeAndPublish", ";clean;compile;test;docker:publish")
