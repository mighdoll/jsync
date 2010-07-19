package com.digiting.util
import java.security.SecureRandom

object RandomIds {
  val random = new SecureRandom
  
  /** Random string using only characters legal in a URI, but not using 
   *  _, !, or ' which we reserve even though they are legal URI characters
   * Approximately 6 bits of randomness per character
   *  
   * from the uri spec: http://www.ietf.org/rfc/rfc2396.txt
   *    unreserved  = alphanum | mark
   *    mark        = "-" | "_" | "." | "!" | "~" | "*" | "'" | "(" | ")"
   */
  def randomUriString(length:Int):String = {    
    val legalChars = 
      "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"  // now uses alphanum only for ease in copy/paste from log files
    val s = new StringBuilder
    0 until length foreach { _ => 
      val dex = random.nextInt(legalChars.length)
      val randomChar = legalChars.charAt(dex)
      s append randomChar
    }
    s.toString
  }
  
  def randomId(length:Int):String = {
    Configuration.getString("UniqueIds") match {
      case Some("debug") => debugId()
      case _ => randomUriString(length)  
    }
  }
  
  var nextId = 0
  def debugId():String = synchronized {
    val result = nextId.toString
    nextId = nextId + 1
    result
  }
}
