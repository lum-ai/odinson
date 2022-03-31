import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

ThisBuild / organization := "ai.lum"

ThisBuild / scalaVersion := "2.12.10"

ThisBuild / fork := true

/* You can print computed classpath by `show compile:fullClassPath`.
 * From that list you can check jar name (that is not so obvious with play dependencies etc).
 */
lazy val documentationSettings = Seq(
  // see https://www.scala-sbt.org/1.x/docs/Howto-Scaladoc.html#Enable+automatic+linking+to+the+external+Scaladoc+of+managed+dependencies
  autoAPIMappings := true,
)

lazy val commonSettings = Seq(
  // show test duration
  Test / testOptions += Tests.Argument("-oD"),
  excludeDependencies += "commons-logging" % "commons-logging",
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
    Docker / packageName := "odinson-extras",
    //mainClass in Compile := Some("ai.lum.odinson.extra.IndexDocuments"),
    //dockerRepository := Some("index.docker.io"),
    //dockerChmodType := DockerChmodType.UserGroupWriteExecute
    Universal / javaOptions ++= Seq(
      "-J-Xmx6G"
      )
    )

// Docker settings
val gitDockerTag = settingKey[String]("Git commit-based tag for docker")
ThisBuild / gitDockerTag := {
  git.gitHeadCommit.value match {
    case Some(commit) => 
      val shortHash: String = commit.take(7)
      val uncommittedChanges: Boolean = (git.gitUncommittedChanges).value
      s"""${shortHash}${if (uncommittedChanges) "-DIRTY" else ""}"""
    case None => "latest"
  }
}

lazy val generalDockerSettings = {
  Seq(
    ThisBuild / parallelExecution := false,
    // see https://www.scala-sbt.org/sbt-native-packager/formats/docker.html
    Docker / daemonUserUid := None,
    Docker / daemonUser  := "odinson",
    Docker / packageName := "odinson-rest-api",
    dockerBaseImage := "eclipse-temurin:11-jre-focal", // arm46 and amd64 compat
    dockerUsername := Some("lumai"),
    dockerAliases ++= Seq(
      dockerAlias.value.withTag(Option("latest")),
      dockerAlias.value.withTag(Option(gitDockerTag.value))
      ),
    Docker / maintainer := "Gus Hahn-Powell <ghp@lum.ai>",
    Universal / javaOptions ++= Seq(
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

ThisBuild / publishTo := sonatypePublishToBundle.value

ThisBuild / publishMavenStyle := true

Test / publishArtifact := false

ThisBuild / licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

ThisBuild / homepage := Some(url("https://github.com/lum-ai/odinson"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/lum-ai/odinson"),
    "scm:git@github.com:lum-ai/odinson.git"
    )
  )

ThisBuild / developers := List(
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
