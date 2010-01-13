import sbt._
import Process._ 

class LiquidJAspects(projectInfo: ProjectInfo) extends DefaultProject(projectInfo) {
  lazy val aspectjPath = Path.fromFile(outputPath.asFile.getParentFile.getParentFile) / "tools" / "aspectj"
  lazy val ajcCmd = aspectjPath / "ajc" absolutePath
  lazy val aspectjrt = aspectjPath  / "lib" / "aspectjrt.jar"
  override lazy val compile = execTask {<x> echo "ajc"</x>} && execTask (<x>
    {ajcCmd}
    -sourceroots {mainJavaSourcePath.absolutePath}
    -1.6 -source 1.6 -target 1.6 
    -cp {aspectjrt}
    -d {mainCompilePath}
    </x>)

  // interim debug routines
  lazy val foo= execTask (<x>printenv CLASSPATH</x>)
  lazy val pwd= execTask (<x>pwd</x>)
  lazy val ajcDir = task { Console println "ajc: " + ajcCmd ; None}
  lazy val javaSourcePath = task { Console println "javaSourcePath: " + mainJavaSourcePath.absolutePath; None}
}

