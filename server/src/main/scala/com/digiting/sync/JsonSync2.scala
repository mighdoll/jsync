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

import _root_.net.liftweb.util._
import _root_.net.liftweb.http._
import collection._

import java.util.concurrent.locks._
import JsonConnection._

object SyncRequestApi {
  def dispatch:PartialFunction[Req, () => Box[LiftResponse]] = {
    case req @ Req("sync" :: Nil, _, PostRequest) =>
      () => sync(req)
  }

  private def sync(req:Req):Box[LiftResponse] = {
    val connection = ActiveConnections.getConnection("default")
    req.body match {
	    case Full(body) => 
	      connection ! ReceiveJsonText(new String(body))
	    case _ => 
	      Log.warn("empty request received to /sync:  should we allow this?")
    }
    
    import JsonSendBuffer._
    val response = connection.sendBuffer !? (1000, Take())
    val responseStr = response match {
      case Some(Pending(json)) => json
      case _ => "[]"
    }
            
    Full(InMemoryResponse(responseStr.getBytes,
        ("Content-Type" -> "application/json") :: Nil, Nil, 200))
  }
}

/** maintains a collection of active connections 
 *  CONSIDER writing this as an actor and using self.receiveWithin to call it... */
object ActiveConnections {
  val connections = new mutable.HashMap[String, JsonConnection]

  def getConnection(connectionId:String):JsonConnection = {
    var active:JsonConnection = null
    
    synchronized {
      active =
        connections get connectionId match {
          case Some(connection) => connection 
          case None => {
            val connection = new JsonConnection; 
            connections + (connectionId -> connection)
            connection
          }
        }      
      }
    
    Console println ("SyncRequestApi.getConnection: " + connectionId + " = " + active)
    active
  }
}

