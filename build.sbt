
name := "akka-http-newrelic"
organization := "com.github.leachbj"
scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http-experimental" % "2.4.5",
  "com.newrelic.agent.java" % "newrelic-api" % "3.28.0" % "provided",
  "com.newrelic.agent.java" % "newrelic-agent" % "3.28.0" % "provided"
)

publishMavenStyle := true
publishArtifact in Test := false
pomIncludeRepository := { _ => false }
publishTo <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
homepage := Some(url("http://github.com/akka-http-newrelic"))
licenses := Seq("MIT" -> url("http://www.opensource.org/licenses/MIT"))
scmInfo :=
  Some(ScmInfo(
    url("https://github.com/leachbj/akka-http-newrelic"),
    "scm:git:git://github.com/leachbj/akka-http-newrelic.git"
  ))
pomExtra :=
  <developers>
    <developer>
      <id>leachbj</id>
      <name>Bernard Leach</name>
      <url>http://github.com/leachbj</url>
    </developer>
  </developers>

