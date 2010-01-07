import sbt._
import Process._ 

class LiquidJAspects(projectInfo: ProjectInfo) extends Project with ExecProject {
  lazy val mix = task {Console println "mix"; None}
  override def info = projectInfo
  override def dependencies = Nil
  override def methods = Map.empty
  override def tasks = Map("compile" -> compile, "foo" -> foo)

  lazy val compile = execTask (<x>ajc -sourceroots src/main/java -1.6 -d target</x>)
  lazy val foo= execTask (<x>printenv CLASSPATH</x>)
}

