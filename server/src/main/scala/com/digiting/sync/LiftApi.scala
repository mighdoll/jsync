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
import ActiveConnections._

/**
 * Hooks to connect sync procesing to Lift http requests
 */
object SyncRequestApi {
  /** attach to the stateful dispatch */
  def dispatch:PartialFunction[Req, () => Box[LiftResponse]] = {
    case req @ Req("sync" :: partition :: Nil, _, PostRequest) =>
      () => sync(req, partition)
  }

  /** receive an incoming message from lift  */
  private def sync(req:Req, partition:String):Box[LiftResponse] = {
    try {
      var responseStr = "[]"
      var processed = false
      // LATER if our server doesn't handle this partition, send a redirect to a different server
      
//	  Console println ("sync request received: " + req + " in partition: " + partition)
     
	  // pass message to the appropriate connection
	  for (body <- req.body; 
	       message <- ParseMessage.parse(new String(body))) {
	    val connection = connectionFor(message)
	    
	    if (!message.isEmpty)
	      connection.receiver ! Receiver.ReceiveMessage(message)
     
    	// fetch any responses that are ready, waiting up to 15sec.
	    import SendBuffer._
	    val response = connection.sendBuffer !? (15000, Take())
	    responseStr = response match {
	      case Some(Pending(json)) => json
	      case _ => 
	      	Console println ("Sync API, no response ready, returning []")
	      	"[]"
	    }
	    Log.info("sync() response for " + connection.connectionId + ":\n" + responseStr);
        processed = true
	  }
      if (!processed) 
        Log.error("error processing request to /sync/" + partition + ": " + req)      

	  Full(InMemoryResponse(responseStr.getBytes,
	                       ("Content-Type" -> "application/json") :: Nil, Nil, 200))     
    } catch {
      case e:Exception => 
        Log.error("exception processing request " + req, e)
	    Full(InMemoryResponse("[]".getBytes,
	                         ("Content-Type" -> "application/json") :: Nil, Nil, 200))        
    }
  }
  
  /** find a Connection based on the token, create a new one if necessary */
  private def connectionFor(message:Message):Connection = {
    message.findControlProperty("#token") match {
      case Some(token:String) => ActiveConnections.findConnection(token)
      case _ =>  
        message.findControlProperty("#start") getOrElse {
          Log.error("message contains neither #token nor #start : " + message.toJson)
        }
        ActiveConnections.createConnection()
    }
  }
  
}