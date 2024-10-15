ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.3"

lazy val root = (project in file("."))
  .settings(
    name := "conduktor",
    libraryDependencies ++= Seq(
      "org.typelevel"         %% "cats-effect"         % "3.5.2",
      "com.github.fd4s"       %% "fs2-kafka"           % "3.5.1",
      "org.http4s"            %% "http4s-dsl"          % "0.23.27",
      "org.http4s"            %% "http4s-ember-server" % "0.23.27",
      "org.http4s"            %% "http4s-circe"        % "0.23.27",
      "io.circe"              %% "circe-generic"       % "0.14.9",
      "io.circe"              %% "circe-core"          % "0.14.9",
      "io.circe"              %% "circe-parser"        % "0.14.9",
      "eu.timepit"            %% "refined"             % "0.11.2",
      "com.github.pureconfig" %% "pureconfig-core"     % "0.17.7",
      "ch.qos.logback"        % "logback-classic"      % "1.5.6"
    )
  )
