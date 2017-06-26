name := """VoteApp"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava, PlayEbean)

scalaVersion := "2.11.7"

javacOptions ++= Seq("-Xlint:deprecation")

// Resolver is needed only for SNAPSHOT versions
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies ++= Seq(
  javaJdbc,
  cache,
  javaWs,
  "org.postgresql" % "postgresql" % "9.4-1206-jdbc42",
  "de.svenkubiak" % "jBCrypt" % "0.4",
  "io.jsonwebtoken" % "jjwt" % "0.7.0"
)

fork in run := true