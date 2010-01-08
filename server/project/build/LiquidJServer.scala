import sbt._
import Process._

class LiquidJServer(info: ProjectInfo) extends DefaultWebProject(info)
{
  lazy val parentPath = Path.fromFile("..")
  lazy val aspects = project(parentPath / "aspects", "Aspects").asInstanceOf[ManagedProject]

  override def deliverProjectDependencies = super.deliverProjectDependencies ++ Seq(aspects.projectID)

  // managed library repositories
  val scalaToolsSnapshotsRespo = ScalaToolsSnapshots
  val configgyRepo = "lag.net" at "http://www.lag.net/repo"
  val jbossRespo = "repository.jboss.org" at "http://repository.jboss.org/maven2"

  val configgy = "net.lag" % "configgy" % "1.3.2"

  // lift
  val liftWebkit = "net.liftweb" % "lift-webkit" % "1.1-M8"
  val liftActor = "net.liftweb" % "lift-actor" % "1.1-M8"
  val liftUtil = "net.liftweb" % "lift-util" % "1.1-M8"
  val servlet = "javax.servlet" % "servlet-api" % "2.5" % "compile"  // is this right? don't need this be there for runtime..

  // apache commons 
  val commonsLang = "commons-lang" % "commons-lang" % "2.4"
  val commonsIO = "commons-io" % "commons-io" % "1.2"
  val commonsCLI = "commons-cli" % "commons-cli" % "1.1"

  // mysql, hibernate, jpa
  val mysql = "mysql" % "mysql-connector-java" % "5.1.6"
  val scalaJPA = "org.scala-libs" % "scalajpa" % "1.1"              // maven build had used a 1.2-SNAPSHOT, is 1.1 OK?
  val hibernate = "org.hibernate" % "hibernate-entitymanager" % "3.3.2.GA"  // FIXME need to exclude jta?
  val c3p0 = "org.hibernate" % "hibernate-c3p0" % "3.3.2.GA"
  val slfj = "org.slf4j" % "slf4j-jdk14" % "1.5.8"
  val ejb = "geronimo-spec" % "geronimo-spec-ejb" % "2.1-rc4"
  val jta = "geronimo-spec" % "geronimo-spec-jta" % "1.0.1B-rc4"   // FIXME should be runtime scope?

  lazy val classpathCompile = task { Console println mainCompileConfiguration.classpath.getPaths.mkString("\n") ; None }
  lazy val classpathJetty = task { Console println webappClasspath.getPaths.mkString("\n") ; None }
  lazy val foo = execTask (<x>ls ../aspects/target/classes/com/digiting/sync/aspects/ </x>)
  lazy val processAspects = execTask (<x> ajc -verbose -aspectpath ../aspects/target/classes/com/digiting/sync/aspects/
    -inpath target/classes/com/digiting/sync/syncable/ -Xlint:ignore -1.6 -d target/classes/com/digiting/sync/syncable </x>)
}


