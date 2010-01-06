package bootstrap.liftweb

import _root_.net.liftweb.util._
import _root_.net.liftweb.http._
import _root_.net.liftweb.http.js._
import _root_.net.liftweb.sitemap._
import _root_.net.liftweb.sitemap.Loc._
import JsCmds._
import JE._
import Helpers._
import _root_.java.sql.{Connection, DriverManager}
import _root_.javax.servlet.http.{HttpServletRequest}
import _root_.com.digiting.sync._

/**
 * A class that's instantiated early and run.  It allows the application
 * to modify lift's environment
 */
class Boot {
  def boot {

    val jsync = Loc("jsync", ("jsync" :: Nil, true), "", Hidden)
    val entries =
      Menu(Loc("Home", List("index"), "Home")) ::
      Menu(jsync) ::
      Nil

    LiftRules.setSiteMap(SiteMap(entries:_*))
    LiftRules.enableLiftGC = false;
    LiftRules.autoIncludeAjax = (_) => false;
    LiftRules.autoIncludeComet = (_) => false;
  }

  /**
   * Force the request to be UTF-8
   */
  private def makeUtf8(req: HttpServletRequest) {
    req.setCharacterEncoding("UTF-8")
  }
}
