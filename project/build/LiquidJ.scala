import sbt._

class LiquidJProject(info: ProjectInfo) extends ParentProject(info) {
  lazy val aspects = project("aspects", "Aspects")
  lazy val server = project("server", "LiquidJServer", aspects)

  lazy val hello = task { println("hello there"); None }
}


