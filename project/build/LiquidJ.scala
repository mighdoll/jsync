import sbt._

class LiquidJProject(info: ProjectInfo) extends ParentProject(info) {
  lazy val aspects = project("aspects", "Observable Aspects")
  lazy val server = project("server", "LiquidJ Server", aspects)

  lazy val hello = task { println("hello there"); None }
}


