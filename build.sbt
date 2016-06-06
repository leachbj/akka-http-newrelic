import scala.xml.transform.{RewriteRule, RuleTransformer}
import scala.xml.{Elem, Node, NodeSeq}
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

import scala.util.Properties.envOrNone

name := "akka-http-newrelic"
organization := "com.github.leachbj"
scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http-core" % "2.4.4" % "provided",
  "com.newrelic.agent.java" % "newrelic-api" % "3.28.0" % "provided",
  "com.newrelic.agent.java" % "newrelic-agent" % "3.28.0" % "provided"
)

packageOptions in(Compile, packageBin) +=
  Package.ManifestAttributes("Weave-Classes" -> "akka.http.scaladsl.HttpExt,akka.http.impl.engine.client.PoolInterfaceActor$PoolRequest,akka.http.impl.engine.client.PoolInterfaceActor,akka.http.impl.engine.client.PoolFlow$RequestContext,akka.http.impl.engine.client.PoolGateway",
    "Reference-Classes" -> "scala.runtime.BoxesRunTime,akka.http.scaladsl.model.headers.Host,akka.stream.Outlet,scala.Product$class,scala.collection.immutable.Seq$,scala.Serializable,akka.stream.BidiShape,akka.http.scaladsl.model.Uri$Authority,akka.stream.scaladsl.BidiFlow,scala.Function1,scala.Some,scala.runtime.AbstractFunction0,scala.runtime.AbstractFunction1,akka.stream.stage.GraphStageLogic,scala.concurrent.Future,akka.stream.Inlet,scala.collection.Iterator,akka.http.impl.engine.client.PoolFlow$ResponseContext,akka.stream.stage.OutHandler$class,scala.StringContext,scala.runtime.ScalaRunTime$,akka.dispatch.ExecutionContexts,scala.concurrent.ExecutionContext,akka.http.scaladsl.model.Uri,scala.collection.immutable.Seq,scala.PartialFunction,scala.Product,scala.collection.mutable.Stack$,scala.None$,akka.http.scaladsl.model.HttpResponse,scala.MatchError,akka.stream.stage.InHandler$class,scala.util.Success,scala.reflect.ClassTag$,scala.collection.immutable.List$,scala.Predef$,scala.Option,scala.Option$,akka.http.scaladsl.model.HttpRequest,akka.stream.scaladsl.BidiFlow$,scala.util.Try,scala.runtime.BoxedUnit,akka.stream.stage.GraphStage,scala.collection.mutable.Stack,akka.stream.Inlet$,scala.Predef$ArrowAssoc$,akka.http.scaladsl.settings.ClientConnectionSettings,akka.stream.Attributes,akka.stream.Shape,scala.collection.immutable.Nil$,akka.stream.actor.ActorSubscriberMessage$OnNext,akka.stream.stage.OutHandler,akka.stream.Outlet$,akka.http.scaladsl.model.headers.RawHeader,scala.Tuple2,akka.actor.ActorRef,akka.http.scaladsl.model.HttpHeader,akka.http.scaladsl.model.Uri$Host,akka.event.LoggingAdapter,akka.stream.stage.InHandler")

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
