import sbt._


class HelloWorldProject(info: ProjectInfo) extends DefaultProject(info)
{
  val configgy = "net.lag" % "configgy" % "1.3.2"
  val configgyRepo = "lag.net" at "http://www.lag.net/repo"
  val scalaToolsRespo = "scala-tools.org" at "http://scala-tools.org/repo-releases"
  val scalaToolsSnapshotsRespo = "snapshots.scala-tools.org" at "http://scala-tools.org/repo-snapshots"
  val jbossRespo = "repository.jboss.org" at "http://repository.jboss.org/maven2"

  lazy val hi = task { println("Hello World"); None }
}
