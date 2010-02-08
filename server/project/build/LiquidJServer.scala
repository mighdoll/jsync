import sbt._
import Process._


class LiquidJServer(info: ProjectInfo) extends DefaultWebProject(info)
{
  lazy val projectRootPath = Path.fromFile(outputPath.asFile.getParentFile.getParentFile) 
  lazy val aspects = project(projectRootPath / "aspects").asInstanceOf[DefaultProject]
  override def deliverProjectDependencies = super.deliverProjectDependencies ++ Seq(aspects.projectID)

  // managed library repositories
  val scalaToolsSnapshotsRespo = ScalaToolsSnapshots
  val configgyRepo = "lag.net" at "http://www.lag.net/repo"
  val jbossRespo = "repository.jboss.org" at "http://repository.jboss.org/maven2"
  val jbossSnapshos = "snapshots.jboss.org" at "http://snapshots.jboss.org/maven2"

  // liquid J libraries
  val configgy = "net.lag" % "configgy" % "1.3.2"
  val liftJson = "net.liftweb" % "lift-json" % "1.1-M8"
  val inifinispan = "org.infinispan" % "infinispan-core" % "4.0.0.CR4"

  // test libraries
  val junit = "junit" % "junit" % "4.8.1" % "test"
  val scalatest = "org.scalatest" % "scalatest" % "1.0" % "test"

  // lift
  val liftWebkit = "net.liftweb" % "lift-webkit" % "1.1-M8"
  val liftActor = "net.liftweb" % "lift-actor" % "1.1-M8"
  val liftUtil = "net.liftweb" % "lift-util" % "1.1-M8"
  val servlet = "javax.servlet" % "servlet-api" % "2.5" % "provided"  
  val jetty6 = "org.mortbay.jetty" % "jetty" % "6.1.21" % "test"


  // required because Ivy doesn't pull repositories from poms
  val smackRepo = "m2-repository-smack" at "http://maven.reucon.com/public"


  // apache commons 
  val commonsLang = "commons-lang" % "commons-lang" % "2.4"
  val commonsIO = "commons-io" % "commons-io" % "1.2"
  val commonsCLI = "commons-cli" % "commons-cli" % "1.1"

  // mysql, hibernate, jpa
  val mysql = "mysql" % "mysql-connector-java" % "5.1.6"
  val scalaJPA = "org.scala-libs" % "scalajpa" % "1.1"              // maven build had used a 1.2-SNAPSHOT, is 1.1 OK?
  val hibernate = "org.hibernate" % "hibernate-entitymanager" % "3.3.2.GA"  // FIXME need to exclude jta?
  val c3p0 = "org.hibernate" % "hibernate-c3p0" % "3.3.2.GA"
  val slfjBinding = "org.slf4j" % "slf4j-log4j12" % "1.5.10"
  val ejb = "geronimo-spec" % "geronimo-spec-ejb" % "2.1-rc4"
  val jta = "geronimo-spec" % "geronimo-spec-jta" % "1.0.1B-rc4"   // FIXME should be runtime scope?

  // attach the aspect compiler to run after the compiler, (could improve this SBT)
  override def compileAction = super.compileAction && processAspects

  lazy val aspectProjectPath = projectRootPath / "aspects" 
  lazy val ajcClassLibs = 
    mainCompilePath.absolutePath ::
    (projectRootPath / "tools" / "aspectj" / "lib" / "aspectjrt.jar").absolutePath :: 
    FileUtilities.scalaLibraryJar.getAbsolutePath :: Nil
  lazy val syncableClasses = (mainCompilePath / "com" / "digiting" / "sync" / "syncable").absolutePath :: Nil

  lazy val aspectCmd = <o> 
    ../tools/aspectj/ajc 
    -showWeaveInfo 
    -aspectpath {Path.fromFile(aspects.mainCompilePath.absolutePath) / 
      "com" / "digiting" / "sync" / "aspects" toString}
    -inpath {syncableClasses mkString(":")}
    -cp {ajcClassLibs mkString(":") }
    -1.6 -source 1.6 -target 1.6
    -d target/classes </o>

  lazy val processAspects = task {Console println aspectCmd.text; None} && execTask(aspectCmd)

  lazy val personalEnv = new PersonalEnvironment(info, log)

  override def jettyRunAction = task {
    System.setProperty("jsyncServerConfig", personalEnv.liquidjConfig.value)
    System.setProperty("jsyncServer_runMode", personalEnv.liquidjRunMode.value)
    None
  } && super.jettyRunAction

  lazy val setServerConfigTest = task {
    Console println ("setServerConfigTest")
    System.setProperty("jsyncServerConfig", "jsync-server.conf")
    System.setProperty("jsyncServer_runMode", "debug")
    None
  }

  override def testAction = super.testAction dependsOn(setServerConfigTest)
}

class PersonalEnvironment(info:ProjectInfo, logger:Logger) extends BasicEnvironment {
  val log = logger
  def envBackingPath = info.builderPath / "personal.properties"

  lazy val liquidjConfig = propertyOptional[String]("jsync-server.conf")
  lazy val liquidjRunMode = propertyOptional[String]("debug")
}

