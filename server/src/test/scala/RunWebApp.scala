import _root_.org.mortbay.jetty.Connector
import _root_.org.mortbay.jetty.Server
import _root_.org.mortbay.jetty.webapp.WebAppContext
import com.digiting.util.Configuration

object RunWebApp  {
  def main(args:Array[String]) {  
    Configuration.initFromVariable("jsyncServerConfig")
    launchJetty()
  }
  
  def launchJetty() {    
    val server = new Server(8080)
    val context = new WebAppContext()
    context.setServer(server)
    context.setContextPath("/")
    context.setWar("src/main/webapp")
  
    server.addHandler(context)
  
    try {
      println(">>> STARTING EMBEDDED JETTY SERVER, PRESS ANY KEY TO STOP");
      server.start();
      while (System.in.available() == 0) {
        Thread.sleep(5000)
      }
      server.stop()
      server.join()
    } catch {
      case exc : Exception => {
        exc.printStackTrace()
        System.exit(100)
      }
    }
  }
}
