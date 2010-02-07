import com.digiting.util.Configuration
import com.digiting.sync.testServer.ClientTestApp

object RunTestWebApp {

  def main(args:Array[String]) {
    Configuration.initFromVariable("jsyncServerConfig")
    ClientTestApp.init()    
    RunWebApp.launchJetty()    
  }
}
