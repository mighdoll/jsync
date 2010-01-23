package com.digiting.sync.testServer
import com.digiting.sync.test.TestApplication
import com.digiting.util.LogHelper
import net.lag.logging.Logger
import net.lag.logging.Logger
import com.digiting.sync.syncable.TestNameObj

object ClientTestApp {
  def init() { 
    TestApplication.registerTestServices(ClientTestResponse)
  }
}

@ImplicitServiceClass("ClientTestResponse")
object ClientTestResponse extends LogHelper {  
  val log = Logger("ClientTestResponse")
  
  @ImplicitService
  def returnBla(ref:Syncable):Syncable = {
    App.withTransientPartition {
      TestNameObj("bla")
    }
  }
}
