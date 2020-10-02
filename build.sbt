name := "zmail"

version := "0.1"

scalaVersion := "2.13.3"

val zioVersion = "1.0.1"

libraryDependencies += "dev.zio"                      %% "zio"                           % zioVersion
libraryDependencies += "dev.zio"                      %% "zio-streams"                   % zioVersion
libraryDependencies += "dev.zio"                      %% "zio-nio"                       % "1.0.0-RC9"
libraryDependencies += "org.polynote"                 %% "uzhttp"                        % "0.2.5"
libraryDependencies += "javax.mail"                   % "javax.mail-api"                 % "1.6.2"
libraryDependencies += "com.sun.mail"                 % "javax.mail"                     % "1.6.2"
libraryDependencies += "javax.activation"             % "activation"                     % "1.1.1"
libraryDependencies += "commons-codec"                % "commons-codec"                  % "1.14"
libraryDependencies += "com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "2.2.0"
libraryDependencies += "org.json4s"                   %% "json4s-native"                 % "3.7.0-M6"
libraryDependencies += "dev.zio"                      %% "zio-test"                      % zioVersion % "test"
libraryDependencies += "dev.zio"                      %% "zio-test-sbt"                  % zioVersion % "test"

lazy val root = (project in file("."))