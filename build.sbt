import ReleaseTransformations._
import com.typesafe.sbt.packager.docker.DockerChmodType

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
    daemonUser in Docker    := "lumai",
    dockerUsername := Some("lumai"),
    dockerAliases ++= Seq(
      dockerAlias.value.withTag(Option("latest")),
      dockerAlias.value.withTag(Option(gitDockerTag.value))
    ),
    maintainer in Docker := "Gus Hahn-Powell <ghp@lum.ai>",
    // "openjdk:11-jre-alpine"
    dockerBaseImage := "openjdk:11",
    javaOptions in Universal ++= Seq(
      "-Dodinson.dataDir=/app/data/odinson"
    )
  )
}

// REST API
lazy val backend = project
  .aggregate(core)
  .dependsOn(core % "test->test;compile->compile", extra % "test->compile;test->test")
  .settings(commonSettings)
  .enablePlugins(PlayScala)
  .settings(
    name := "odinson-rest-api",
    libraryDependencies ++= {
      val akkaV = "2.6.6"
      Seq(
        guice,
        jdbc,
        caffeine,
        ws,
        "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test,
        "com.typesafe.akka"      %% "akka-actor-typed"   % akkaV,
        "com.typesafe.akka"      %% "akka-protobuf"      % akkaV,
        "com.typesafe.akka"      %% "akka-actor"         % akkaV,
        "com.typesafe.akka"      %% "akka-slf4j"         % akkaV,
        "com.typesafe.akka"      %% "akka-remote"        % akkaV,
        "com.typesafe.akka"      %% "akka-stream"        % akkaV
      )
    },
    //-Dpidfile.path=/dev/null
    // Dev settings which are read prior to loading of config.
    // See https://www.playframework.com/documentation/2.7.x/ConfigFile#Using-with-the-run-command
    PlayKeys.devSettings += "play.server.http.port" -> "9000",
    PlayKeys.devSettings += "play.server.http.address" -> "0.0.0.0",
    PlayKeys.devSettings += "play.server.http.idleTimeout" -> "infinite"
  )
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(generalDockerSettings)
  .settings(
    packageName in Docker := "odinson-rest-api",
    mainClass in Compile := Some("play.core.server.ProdServerStart"),
    //dockerRepository := Some("index.docker.io"),
    dockerExposedPorts in Docker := Seq(9000),
    javaOptions in Universal ++= Seq(
      "-J-Xmx4G",
      // avoid writing a PID file
      "-Dplay.server.pidfile.path=/dev/null",
      //"-Dplay.server.akka.requestTimeout=20s"
      "-Dlogger.resource=odinson-rest-logger.xml"
    )
  )

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
