scalacOptions ++= Seq(
  "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
  "-encoding", "utf-8",                // Specify character encoding used by source files.
  "-explaintypes",                     // Explain type errors in more detail.
  "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
  "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
  "-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
  "-Xfuture",                          // Turn on future language features.
  "-Xlint:adapted-args",               // Warn if an argument list is modified to match the receiver.
  "-Xlint:by-name-right-associative",  // By-name parameter of right associative operator.
  "-Xlint:constant",                   // Evaluation of a constant arithmetic expression results in an error.
  "-Xlint:delayedinit-select",         // Selecting member of DelayedInit.
  "-Xlint:doc-detached",               // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Xlint:nullary-unit",               // Warn when nullary methods return Unit.
  "-Xlint:option-implicit",            // Option.apply used implicit view.
  "-Xlint:package-object-classes",     // Class or object defined in package object.
  "-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
  "-Xlint:unsound-match",              // Pattern match may not be typesafe.
  "-Yno-adapted-args",                 // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
  "-Ypartial-unification",             // Enable partial unification in type constructor inference
  "-Ywarn-dead-code",                  // Warn when dead code is identified.
  "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
  "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
  "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
  "-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.
  "-Ywarn-numeric-widen",              // Warn when numerics are widened.
  "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
  "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
  "-Ywarn-unused:locals",              // Warn if a local definition is unused.
  "-Ywarn-unused:params",              // Warn if a value parameter is unused.
  "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
  "-Ywarn-unused:privates",            // Warn if a private member is unused.
  "-Ywarn-value-discard"               // Warn when non-Unit expression results are unused.
)

lazy val commonSettings = Seq(
  organization := "ai.lum",
  scalaVersion in ThisBuild := "2.12.7",
  // the console can't really cope with some scalac flags
  scalacOptions in (Compile, console) --= Seq("-Ywarn-unused:imports", "-Xfatal-warnings"),
  // show test duration
  testOptions in Test += Tests.Argument("-oD"),
  excludeDependencies += "commons-logging" % "commons-logging",
  // used for DocumentMetadata
  resolvers += "Lum AI public snapshots" at "https://s3-us-west-2.amazonaws.com/maven.lum.ai/snapshots"
)

lazy val core = project
  .enablePlugins(BuildInfoPlugin)
  .settings(commonSettings)
  .settings(
    buildInfoPackage := "ai.lum.odinson",
    buildInfoOptions += BuildInfoOption.BuildTime,
    buildInfoKeys := Seq[BuildInfoKey](
      name, version, scalaVersion, sbtVersion, libraryDependencies, scalacOptions,
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


lazy val restApi = taskKey[Unit]("Launches the odinson REST API.")
restApi := (run in Compile in backend).toTask("").value