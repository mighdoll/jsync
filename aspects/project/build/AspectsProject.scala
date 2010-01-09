import sbt._
import Process._ 

class LiquidJAspects(projectInfo: ProjectInfo) extends DefaultProject(projectInfo) {
  override lazy val compile = execTask (<x>
    ../tools/aspectj/ajc 
    -sourceroots src/main/java -1.6 -source 1.6 -target 1.6 
    -d target 
    -cp ../tools/aspectj/lib/aspectjrt.jar: </x>)

  lazy val foo= execTask (<x>printenv CLASSPATH</x>)
}

