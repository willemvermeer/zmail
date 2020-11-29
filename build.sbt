import sbt.Keys.libraryDependencies

name := "zmail"

version := "0.1"

scalaVersion := "2.13.3"

val grpcVersion      = "1.30.2"
val zioVersion       = "1.0.3"
val zioNioVersion    = "1.0.0-RC10"
val zioConfigVersion = "1.0.0-RC29"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

lazy val root = (project in file("."))
  .aggregate(
    protos.jvm,
    protos.js,
    server,
    webapp
  )

lazy val protos = crossProject(JSPlatform, JVMPlatform)
  .in(file("protos"))
  .settings(
    PB.targets in Compile := Seq(
      scalapb.gen(grpc = true)          -> (sourceManaged in Compile).value,
      scalapb.zio_grpc.ZioCodeGenerator -> (sourceManaged in Compile).value
    ),
    PB.protoSources in Compile := Seq(
      (baseDirectory in ThisBuild).value / "protos" / "src" / "main" / "protobuf"
    )
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %%% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
    )
  )

lazy val server = project
  .dependsOn(protos.jvm)
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"              %% "zio-streams"          % zioVersion,
      "dev.zio"              %% "zio-nio"              % zioNioVersion,
      "dev.zio"              %% "zio-config"           % zioConfigVersion,
      "dev.zio"              %% "zio-config-magnolia"  % zioConfigVersion,
      "dev.zio"              %% "zio-config-typesafe"  % zioConfigVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
      "io.grpc"               % "grpc-netty"           % grpcVersion,
      "io.grpc"               % "grpc-services"        % grpcVersion,
      "com.lihaoyi"          %% "fastparse"            % "2.3.0",
      "com.sun.mail"          % "javax.mail"           % "1.6.2",
      "javax.activation"      % "activation"           % "1.1.1"
    ),
    libraryDependencies ++= Seq(
      "dev.zio"       %% "zio-test"     % zioVersion % "test",
      "dev.zio"       %% "zio-test-sbt" % zioVersion % "test",
      "org.scalatest" %% "scalatest"    % "3.2.0" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", xs @ _*) =>
        (xs map {_.toLowerCase}) match {
          case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) => MergeStrategy.discard
          case _ => MergeStrategy.last
        }
      case _ => MergeStrategy.first
    },
    run / fork := true
  )

lazy val webapp = project
  .enablePlugins(ScalaJSPlugin)
  .enablePlugins(ScalaJSBundlerPlugin)
  .dependsOn(protos.js)
  .settings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
    webpackBundlingMode := BundlingMode.LibraryOnly(),
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-time" % "2.0.0",
      "org.scala-js"      %%% "scalajs-dom"     % "1.0.0"
    ),
    libraryDependencies ++= Seq(
      "dev.zio"       %% "zio-test"     % zioVersion % "test",
      "dev.zio"       %% "zio-test-sbt" % zioVersion % "test",
      "org.scalatest" %% "scalatest"    % "3.2.0" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    scalaJSUseMainModuleInitializer := true,
  )