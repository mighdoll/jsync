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

import collection._
import actors.Actor._
import actors.Actor

/**
 * Maintains a collection of active connections from clients to this
 * server.  
 * 
 * CONSIDER replace with synchronized{} blocks - it should be simpler (see scala synchronized map, scala collections jcl)
 */
object ActiveConnections extends Actor {
  case class Get(connectionId:String)	// request message to get an existing connection
  case class Create()					// request message to create a new connection
  var createdCount = 0;
  
  val connections = new mutable.HashMap[String, Connection]	// currently active client connections 
  start
  
  def findConnection(id:String):Connection = {
    this !? Get(id) match {
      case Some(connection:Connection) => connection
      case None => throw new NotYetImplemented	// connection lost or expired, need to reset the client
    }
  }
  
  def createConnection():Connection = {
    val connection = this !? Create() 
    connection match { 
      case c:Connection => c
      case _ => throw new ImplementationError
    }
  }
  
  def act() {
    loop {
      react {
        case Get(id) => reply(connections get id)          
        case Create() => reply(create) 
      }
    }
  }

  /** create a new active connection */  
  private def create:Connection = {
    // LATER make this more unique across reboots.  e.g. serverForThisParititionCount-createdCount-randomId
    val id = createdCount + "-" + secureRandomUriString(10) 
    createdCount += 1
    
    val connection = new Connection(id); 
    connections + (id -> connection)    
    
    // queue up a message containing the token 
    val tokenMap = new JsonObject.MutableJsonMap
    tokenMap + ("#token" -> id)
    val message = new Message(tokenMap :: Nil)
    connection.sendBuffer.unsafeQueueMessage(message) 
    connection
  }

  /** return a random string containing characters legal in uri parameter 
   * the random number is not cryptographically strong */
  import java.util.Random
  private def insecureRandomUriString(length:Int):String = {
    randomUriString(length, new Random)
  }
  
  import java.security.SecureRandom
  private def secureRandomUriString(length:Int):String = {
    randomUriString(length, new SecureRandom)
  }
  
  /** random character string, guaranteed not to include '-', '/' or '=' */
  def randomUriString(length:Int, random:Random):String = {    
    val legalChars = "1234567890abcdefghijklmnopqrstuvwxyaABCDEFGHIJKLMNOPQRSTUVWXYZ$-_.+!*(),"
    val s = new StringBuilder
    0 until length foreach { _ =>	
      val dex = random.nextInt(legalChars.length)
      val randomChar = legalChars.charAt(dex)
      s append randomChar
    }
    s.toString
  }

  // LATER expire unused connections after awhile.
}

