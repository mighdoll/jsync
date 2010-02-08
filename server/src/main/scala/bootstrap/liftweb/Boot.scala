package bootstrap.liftweb

import _root_.net.liftweb.util._
import _root_.net.liftweb.common._
import _root_.net.liftweb.http._
import _root_.net.liftweb.http.js._
import _root_.net.liftweb.sitemap._
import _root_.net.liftweb.sitemap.Loc._
import _root_.net.liftweb.http.provider._
import JsCmds._
import JE._
import Helpers._
import _root_.com.digiting.sync._
import net.lag.logging.Logger
import com.digiting.sync.Start

/**
 * A class that's instantiated early and run.  It allows the application
 * to modify lift's environment
 */
class Boot {
  def boot {
    val log = Logger("Boot")  
    log.info("starting Boot!")
    Start.start()

    // where to search for snippets
    LiftRules.addToPackages("com.digiting.jsync-testServer")

    // Build SiteMap
    val all = Loc("all", (Nil, true), "", Hidden)   // allow everything under /
    val entries = Menu(Loc("Home", List("index"), "Home")) ::           
      Menu(all) ::
      Nil


    val ignoreStaticFiles:PartialFunction[Req,Boolean] = {
      case req if (isStatic(req)) => false
    }

    // allow these directories to be served by the container (jetty)
    LiftRules.liftRequest.append(ignoreStaticFiles) 
    
    LiftRules.setSiteMap(SiteMap(entries:_*))
    LiftRules.enableLiftGC = false;
    LiftRules.autoIncludeAjax = (_) => false;
    LiftRules.autoIncludeComet = (_) => false;
    LiftRules.useXhtmlMimeType = false;
    
    /*
     * Show the spinny image when an Ajax call starts
     */
    LiftRules.ajaxStart =
    Full(() => LiftRules.jsArtifacts.show("ajax-loader").cmd)

    /*
     * Make the spinny image go away when it ends
     */
    LiftRules.ajaxEnd =
    Full(() => LiftRules.jsArtifacts.hide("ajax-loader").cmd)

    LiftRules.early.append(makeUtf8)

    LiftRules.dispatch.prepend(SyncRequestApi.dispatch)
  }

    
    /**
     * Force the request to be UTF-8
     */
    private def makeUtf8(req: HTTPRequest) {
        req.setCharacterEncoding("UTF-8")
    }
    
    private def isStatic(req:Req):Boolean = {
      req.path.partPath match {
        case "static" :: _ => true
        case "js" :: _ => true
        case "jslib" :: _ => true
        case "jsync" :: _ => true
        case "plugins" :: _ => true
        case "images" :: _ => true
        case "style" :: _ => true
        case _ => false
      }
    }

}

