import sbt._

class LiquidJServer(info: ProjectInfo) extends DefaultProject(info)
{
  val configgyRepo = "lag.net" at "http://www.lag.net/repo"
  val scalaToolsRespo = "scala-tools.org" at "http://scala-tools.org/repo-releases"
  val scalaToolsSnapshotsRespo = "snapshots.scala-tools.org" at "http://scala-tools.org/repo-snapshots"
  val jbossRespo = "repository.jboss.org" at "http://repository.jboss.org/maven2"

  val configgy = "net.lag" % "configgy" % "1.3.2"
  val mysql = "mysql" % "mysql-connector-java" % "5.1.6"
  val liftWebkit = "net.liftweb" % "lift-webkit" % "1.1-M8"
  val liftActor = "net.liftweb" % "lift-actor" % "1.1-M8"
  val liftUtil = "net.liftweb" % "lift-util" % "1.1-M8"
  val servlet = "javax.servlet" % "servlet-api" % "2.5" % "compile"  // is this right?
  val commonsLang = "commons-lang" % "commons-lang" % "2.4"
  val commonsIO = "commons-io" % "commons-io" % "1.2"
  val commonsCLI = "commons-cli" % "commons-cli" % "1.1"


  lazy val hi = task { println("Hello World"); None }
}
