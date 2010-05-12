import sbt._
import Process._ 

class LiquidJAspects(projectInfo: ProjectInfo) extends DefaultProject(projectInfo) {
  lazy val aspectjPath = Path.fromFile(outputPath.asFile.getParentFile.getParentFile.getParentFile) / "tools" / "aspectj"
  lazy val ajcCmd = aspectjPath / "ajc" absolutePath
  lazy val aspectjrt = aspectjPath  / "lib" / "aspectjrt.jar"
  lazy val fullCmd = <x>
    {ajcCmd}
    -sourceroots {mainJavaSourcePath.absolutePath}
    -1.6 -source 1.6 -target 1.6 
    -cp {aspectjrt}
    -d {mainCompilePath.absolutePath}
    </x>

  override lazy val compile = task{Console println fullCmd.text; None} && execTask(fullCmd)
}

