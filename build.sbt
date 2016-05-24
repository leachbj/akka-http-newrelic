import sbt.File
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

import scala.util.Properties.envOrNone
import scala.xml.transform.{RewriteRule, RuleTransformer}
import scala.xml.{Elem, Node, NodeSeq}

name := "akka-http-newrelic"
organization := "com.github.leachbj"
scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http-experimental" % "2.4.5" % "provided",
  "com.newrelic.agent.java" % "newrelic-api" % "3.28.0" % "provided",
  "com.newrelic.agent.java" % "newrelic-agent" % "3.28.0" % "provided"
)

// disable using the Scala version in output paths and artifacts
crossPaths := false

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

pomPostProcess := (node => removeDependencies.transform(node).head)

def removeDependencies: RuleTransformer =
  new RuleTransformer(new RewriteRule {
    override def transform(node: Node): NodeSeq = node match {
      case e: Elem if e.label == "dependency" => NodeSeq.Empty
      case _ => node
    }
  })

releasePublishArtifactsAction := PgpKeys.publishSigned.value

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  ReleaseStep(action = Command.process("publishSigned", _)),
  setNextVersion,
  commitNextVersion,
  ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
  pushChanges
)

pgpPassphrase := envOrNone("GPG_PASSPHRASE").map(_.toCharArray)
pgpPublicRing := new File("deploy/pubring.gpg")
pgpSecretRing := new File("deploy/secring.gpg")

credentials ++= (for {
  username <- Option(System.getenv().get("SONATYPE_USERNAME"))
  password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
} yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq