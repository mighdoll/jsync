/*   Copyright [2009] Digiting Inc
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.digiting.sync
import java.security.SecureRandom

class ProtocolException(message:String) extends Exception(message) {
  def this() = this("")
}

class NotYetImplemented(message:String) extends Exception(message) {
  def this() = this("")
}

class ImplementationError(message:String) extends Exception(message) {
  def this() = this("")
}

object NYI {
  def apply() = throw new NotYetImplemented
}

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
      "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ~*().-"
    val s = new StringBuilder
    0 until length foreach { _ =>	
      val dex = random.nextInt(legalChars.length)
      val randomChar = legalChars.charAt(dex)
      s append randomChar
    }
    s.toString
  }
  
}

object StringUtil {
  def firstLine(s:String) = {
    s.lines.take(1).mkString    
  }
}
