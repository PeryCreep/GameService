ThisBuild / version := "0.0.1"

ThisBuild / scalaVersion := "2.13.12"

lazy val root = (project in file("."))
  .settings(
    name := "GameService"
  )

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % "0.23.18",
  "org.http4s" %% "http4s-blaze-server" % "0.23.14",
  "org.http4s" %% "http4s-circe" % "0.23.18",
  "org.http4s" %% "http4s-blaze-client" % "0.23.14",
  "org.http4s" %% "http4s-circe" % "0.23.18",
  "org.http4s" %% "http4s-client" % "0.23.24",
  "com.typesafe.slick" %% "slick" % "3.4.1",
  "org.postgresql" % "postgresql" % "42.5.4",
  "org.typelevel" %% "cats-core" % "2.9.0"
)