import sbt._

class LiquidJServer(info: ProjectInfo) extends DefaultWebProject(info)
{
  val configgyRepo = "lag.net" at "http://www.lag.net/repo"
  val scalaToolsSnapshotsRespo = ScalaToolsSnapshots
  val jbossRespo = "repository.jboss.org" at "http://repository.jboss.org/maven2"

  val configgy = "net.lag" % "configgy" % "1.3.2"

  // lift
  val liftWebkit = "net.liftweb" % "lift-webkit" % "1.1-M8"
  val liftActor = "net.liftweb" % "lift-actor" % "1.1-M8"
  val liftUtil = "net.liftweb" % "lift-util" % "1.1-M8"
  val servlet = "javax.servlet" % "servlet-api" % "2.5" % "compile"  // is this right? shouldn't be there for runtime..

  val commonsLang = "commons-lang" % "commons-lang" % "2.4"
  val commonsIO = "commons-io" % "commons-io" % "1.2"
  val commonsCLI = "commons-cli" % "commons-cli" % "1.1"

  // mysql, hibernate, jpa
  val mysql = "mysql" % "mysql-connector-java" % "5.1.6"
  val scalaJPA = "org.scala-libs" % "scalajpa" % "1.1"    // had used 1.2-SNAPSHOT, is 1.1 OK?
  val hibernate = "org.hibernate" % "hibernate-entitymanager" % "3.3.2.GA"  // FIXME need to exclude jta
  val c3p0 = "org.hibernate" % "hibernate-c3p0" % "3.3.2.GA"
  val slfj = "org.slf4j" % "slf4j-jdk14" % "1.5.8"
  val ejb = "geronimo-spec" % "geronimo-spec-ejb" % "2.1-rc4"
  val jta = "geronimo-spec" % "geronimo-spec-jta" % "1.0.1B-rc4"   // FIXME should be runtime scope

//  val sublimeSimpleDB = "org.sublime" % "sublime-simpleDB" % "0.9-SNAPSHOT" from "file:///home/lee/projects/jsync/server/lib/sublime-simpleDB-0.9-SNAPSHOT.jar"

  lazy val classpathCompile = task { Console println mainCompileConfiguration.classpath.getPaths.mkString("\n") ; None }
  lazy val classpathJetty = task { Console println webappClasspath.getPaths.mkString("\n") ; None }
}
