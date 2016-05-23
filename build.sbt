import scala.xml.transform.{RewriteRule, RuleTransformer}
import scala.xml.{Elem, Node, NodeSeq}
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

import scala.util.Properties.envOrNone

name := "akka-http-newrelic"
organization := "com.github.leachbj"
scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http-experimental" % "2.4.5" % "provided",
  "com.newrelic.agent.java" % "newrelic-api" % "3.28.0" % "provided",
  "com.newrelic.agent.java" % "newrelic-agent" % "3.28.0" % "provided"
)

packageOptions in(Compile, packageBin) +=
  Package.ManifestAttributes("Weave-Classes" -> "akka.http.scaladsl.HttpExt,akka.http.impl.engine.client.PoolInterfaceActor$PoolRequest,akka.http.impl.engine.client.PoolInterfaceActor,akka.http.impl.engine.client.PoolFlow$RequestContext,akka.http.scaladsl.HttpExt\"",
    "References-Classes" -> "akka.http.impl.engine.client.PoolInterfaceActor,akka.http.impl.engine.client.PoolInterfaceActor$PoolRequest")

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

pomPostProcess := (node => excludeAllDependencies.transform(node).head)

def excludeAllDependencies: RuleTransformer =
  new RuleTransformer(new RewriteRule {
    override def transform(node: Node): NodeSeq = node match {
      case e: Elem if e.label == "dependency" => NodeSeq.Empty
      case _ => node
    }
  })

pgpPassphrase := envOrNone("GPG_PASSPHRASE").map(_.toCharArray)
pgpPublicRing := file("deploy/pubring.gpg")
pgpSecretRing := file("deploy/secring.gpg")

credentials ++= (for {
  username <- Option(System.getenv().get("SONATYPE_USERNAME"))
  password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
} yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq
