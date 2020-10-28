import sbt.Keys.libraryDependencies

name := "zmail"

version := "0.1"

scalaVersion := "2.13.3"

val grpcVersion = "1.30.2"
val zioVersion = "1.0.1"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

lazy val root = (project in file("."))
  .aggregate(
    protos.jvm,
    protos.js,
    server,
    client,
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
      "dev.zio" %% "zio-streams" % zioVersion,
      "dev.zio" %% "zio-nio"     % "1.0.0-RC10",
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
      "io.grpc"               % "grpc-netty"           % grpcVersion,
      "io.grpc"               % "grpc-services"        % grpcVersion,
      "com.lihaoyi" %% "fastparse" % "2.3.0",
      "com.sun.mail"     % "javax.mail" % "1.6.2",
      "javax.activation" % "activation" % "1.1.1"
),
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test"          % zioVersion % "test",
      "dev.zio" %% "zio-test-sbt"      % zioVersion % "test",
      "org.scalatest" %% "scalatest" % "3.2.0" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    run / fork := true
  )

lazy val client = project
  .dependsOn(protos.jvm)
  .settings(
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
      "io.grpc"               % "grpc-netty"           % grpcVersion,
    ),
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
    scalaJSUseMainModuleInitializer := true,
  )

mainClass in assembly := Some("email.fizzle.zmail.MailServer")
