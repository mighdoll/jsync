import sbt._
import Process._ 

class LiquidJAspects(projectInfo: ProjectInfo) extends DefaultProject(projectInfo) {
  override lazy val compile = execTask (<x>
    ../tools/aspectj/ajc 
    -sourceroots src/main/java 
    -1.6 -source 1.6 -target 1.6 
    -cp ../tools/aspectj/lib/aspectjrt.jar: 
    -d target 
    </x>)

  lazy val foo= execTask (<x>printenv CLASSPATH</x>)
}

