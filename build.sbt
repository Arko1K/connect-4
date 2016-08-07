name := """connect-4"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  cache,
  "com.typesafe.play.modules" %% "play-modules-redis" % "2.5.0",
  "org.mongodb.morphia" % "morphia" % "1.2.1"
)
