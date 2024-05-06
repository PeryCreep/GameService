ThisBuild / version := "0.0.1"

ThisBuild / scalaVersion := "2.13.12"

lazy val root = (project in file("."))
  .settings(
    name := "GameService"
  )

lazy val jwtDependencies = Seq(
  "dev.profunktor" %% "http4s-jwt-auth" % "1.2.2",
  "com.github.jwt-scala" %% "jwt-circe" % "9.4.6",
  "io.jsonwebtoken" % "jjwt" % "0.9.1"
)

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % "0.23.18",
  "org.http4s" %% "http4s-blaze-server" % "0.23.14",
  "org.http4s" %% "http4s-circe" % "0.23.18",
  "org.http4s" %% "http4s-blaze-client" % "0.23.14",
  "org.http4s" %% "http4s-circe" % "0.23.18",
  "org.http4s" %% "http4s-client" % "0.23.24",
  "io.circe" %% "circe-generic" % "0.14.6",
  "io.circe" %% "circe-parser" % "0.14.7",
  "org.http4s" %% "http4s-circe" % "0.23.18",
  "com.typesafe.slick" %% "slick" % "3.5.0",
  "org.postgresql" % "postgresql" % "42.7.3",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.5.1",
  "org.typelevel" %% "cats-core" % "2.10.0",
  "co.fs2" %% "fs2-core" % "3.10.2",
  "ch.qos.logback" % "logback-classic" % "1.5.6",
  "com.github.pureconfig" %% "pureconfig" % "0.17.6"
) ++ jwtDependencies