package com.digiting.sync
import com.digiting.util.Configuration
import com.digiting.sync.testServer.ClientTestApp

object Start {
  def start() {
    Configuration.initFromVariable("jsyncServerConfig")
    Configuration.getString("testServer") foreach {_=>
      ClientTestApp.init()          
    }

  }
}
