package com.digiting.sync

object CurrentProtocolVersion {
  def version = "0.2"
  
}

object ProtocolVersion {
  private var testVersion = None:Option[String]
  def withTestVersion[T](version:String)(body: =>T):T = {
    val orig = testVersion
    testVersion = Some(version)
    try {
      body
    } finally {
      testVersion = orig
    }
  }
    
  def version = testVersion getOrElse CurrentProtocolVersion.version
}
