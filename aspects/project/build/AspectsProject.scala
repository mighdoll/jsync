import sbt._
import Process._ 

class LiquidJAspects(projectInfo: ProjectInfo) extends DefaultProject(projectInfo) {
  //override def info = projectInfo
  //override def dependencies = Nil
 // override def methods = Map.empty
//  override def tasks = Map("compile" -> compile, "foo" -> foo)

  override lazy val compile = execTask (<x>ajc -sourceroots src/main/java -1.6 -source 1.6 -target 1.6 -d target</x>)
  lazy val foo= execTask (<x>printenv CLASSPATH</x>)
}

