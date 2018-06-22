import sbt.Keys._
import sbt._
// import com.github.play2war.plugin._

name := "jh-fbot"

version := "0.0.1"


val workaround = {
  sys.props += "packaging.type" -> "jar"
  ()
}

organization := "ai.jh"

scalaVersion := "2.12.6"

lazy val akkaVersion = "2.5.6"

aggregate in runMain := true

libraryDependencies ++= Seq(
  "javax.ws.rs" % "javax.ws.rs-api" % "2.1",
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "ch.qos.logback" % "logback-core" % "1.1.7",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.7.0",
  "com.typesafe" % "config" % "1.3.2",
"org.knowm.xchange" % "xchange-bitfinex" % "4.3.1"
)



resolvers += "Local Repository" at "file://" + Path.userHome.absolutePath + "/.ivy2/local"

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

retrieveManaged := true

publishMavenStyle := true

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials.cai")

publishArtifact in (Test, packageBin) := true
publishArtifact in Test := true

publishTo := {
  val nexus = "http://maven.company.ai:8081/repository/companyai-"
  if (isSnapshot.value) {
    Some("snapshots" at nexus + "snapshots")
  } else {
    Some("releases" at nexus + "releases")
  }
}
