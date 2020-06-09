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
      name, version, scalaVersion, sbtVersion,
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


lazy val backendAssemblySettings = {
  mainClass in assembly := Some("play.core.server.ProdServerStart")
  fullClasspath in assembly += Attributed.blank(PlayKeys.playPackageAssets.value)

  assemblyMergeStrategy in assembly := {
    case manifest if manifest.contains("MANIFEST.MF") =>
      // We don't need manifest files since sbt-assembly will create
      // one with the given settings
      MergeStrategy.discard
    case referenceOverrides if referenceOverrides.contains("reference-overrides.conf") =>
      // Keep the content for all reference-overrides.conf files
      MergeStrategy.concat
    case logback if logback.endsWith("logback.xml")     => MergeStrategy.first
    case netty if netty.endsWith("io.netty.versions.properties") => MergeStrategy.first
    case "messages"                                     => MergeStrategy.concat
    case PathList("META-INF", "terracotta", "public-api-types")  => MergeStrategy.concat
    case PathList("play", "api", "libs", "ws", xs @ _*) => MergeStrategy.first
    case PathList("javax", "servlet", xs @ _*)          => MergeStrategy.first
    case PathList("javax", "transaction", xs @ _*)      => MergeStrategy.first
    case PathList("javax", "mail", xs @ _*)             => MergeStrategy.first
    case PathList("javax", "activation", xs @ _*)       => MergeStrategy.first
    case x =>
      // For all the other files, use the default sbt-assembly merge strategy
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
}

lazy val backendDockerSettings = {
  Seq(
    dockerfile in docker := {
      val targetDir = "/app"
      // the assembly task generates a fat jar
      val artifact: File = assembly.value
      val artifactTargetPath = s"$targetDir/${artifact.name}"
      // val productionConf = "production.conf"
      new Dockerfile {
        from("openjdk:11")
        add(artifact, artifactTargetPath)
        // copy(new File(productionConf), file(s"$targetDir/$productionConf"))
        entryPoint("java", "-Dpidfile.path=/dev/null", //s"-Dconfig.file=$targetDir/$productionConf",
          "-jar", artifactTargetPath)
      }
    },
    imageNames in docker := {
      val commit = git.gitHeadCommit.value.getOrElse(s"v${version.value}")
      Seq(
        // sets the latest tag
        ImageName(s"${organization.value}/${name.value}:latest"),
        // use git hash
        ImageName(s"${organization.value}/${name.value}:${commit}"),
        // sets a name with a tag that contains the project version
        ImageName(
          namespace = Some(organization.value),
          repository = name.value,
          tag = Some("v" + version.value)
        )
      )
    }
  )
}

lazy val backend = project
  .aggregate(core)
  .dependsOn(core % "test->test;compile->compile")
  .dependsOn(extra)
  .settings(commonSettings)
  .enablePlugins(PlayScala)
  .settings(
    name := "odinson-rest-api",
    libraryDependencies ++= {
      val akkaV = "2.5.4"
      Seq(
        guice,
        jdbc,
        ehcache,
        ws,
        "org.scalatestplus.play"  %% "scalatestplus-play" % "3.0.0" % Test,
        "com.typesafe.akka" %% "akka-protobuf" % akkaV,
        "com.typesafe.akka" %% "akka-actor" % akkaV,
        "com.typesafe.akka" %% "akka-slf4j" % akkaV,
        "com.typesafe.akka" %% "akka-remote" % akkaV,
        "com.typesafe.akka" %% "akka-stream" % akkaV,
      )
    },
    //-Dpidfile.path=/dev/null
    // Dev settings which are read prior to loading of config.
    // See https://www.playframework.com/documentation/2.7.x/ConfigFile#Using-with-the-run-command
    PlayKeys.devSettings += "play.server.http.port" -> "9000",
    PlayKeys.devSettings += "play.server.http.address" -> "0.0.0.0",
    PlayKeys.devSettings += "play.server.http.idleTimeout" -> "infinite"
  )
  .settings(backendAssemblySettings)
  .settings(backendDockerSettings)



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


// tasks
addCommandAlias("dockerizeRestApi", ";clean;compile;test;backend/docker")
