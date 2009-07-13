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
import JsonUtil._
import collection._

/**
 * Utility routine to parse a Message from a json string.
 * 
 * CONSIDER -- move this into Message? 
 */
object ParseMessage {
  /** parses a string containing a json transaction message from the client.  */
  def parse(receivedXact:String):Option[Message] = {
//    Log.info("ParseMessage.receiveTransaction " + receivedXact)
    var message:Option[Message] = None
    
	// extract the transaction number object and the transaction body objects
	JSONParser.parse(receivedXact) match {
	  case Full(json) => 
        processJson(json) 
          {jsonArray => message = parseJsonMessageBody(jsonArray) } 
          {jsonObj => Log.error("protocol error: sync messages should start be enclosed in json array []:\n" + receivedXact);}
	  case _ => 
	    Log.error("hard to parse transaction received: " + receivedXact)	           
	}
    message
  }  

  
  /** pull out the parts of the json transaction message  */
  private def parseJsonMessageBody(elems:List[_]):Option[Message] = {
    var xactNumber:Option[Int] = None
    val edits = new collection.mutable.ListBuffer[Map[String,Any]]
    val syncs = new collection.mutable.ListBuffer[Map[String,Any]]
    val controls = new collection.mutable.ListBuffer[Map[String,Any]]
    var reconnecting = false  // if the client is continuing a sync session (otherwise we reset to a fresh state)

    // parse the json string into json objects, separating out control messages e.g. #transaction
  	// SOON (scala) -- change this to use ProtocolComponent subclasses e.g. TransactionProtocolComponent, EditProtocolComponent.  Use unapply and pattern matching..
    elems foreach {json => processJson(json)
      {jsonArray => Log.error("protocol error: sync protocol messages should not have nested json arrays")}
      {jsonObj => {
        { jsonObj get "#transaction" map { number => 
        	xactNumber = Some(jsonValueToInt(number)) 
          } 
        } orElse {
          jsonObj get "#edit" map {_ => 
            edits + jsonObj; 
          }               
        } orElse {
          jsonObj find { case(name:String,value) =>
            name.charAt(0) == '#'
          } map { _ => 
            controls + jsonObj  
          }
          // SCALA: map returns None if the find returns None, so orElse works.
        } orElse {
          jsonObj get "id" map {_ => syncs + jsonObj}
        } orElse {
          Log.error("protocol error: unexpected object received: " + jsonObj); None
        }
      }}
    }
    
    val message = xactNumber match { 
      case Some(num) =>
        Some(new Message(num, controls.toList, edits.toList, syncs.toList))
      case None => 
        if (edits.isEmpty && syncs.isEmpty) {
          if (controls.length == 1 && (controls.first contains "#token")) {
            Some(new Message(-1, controls.toList, edits.toList, syncs.toList))
          } else {
            Log.error("protocol error: no transaction number recieved " + elems)
            None
          }  
        } else {
          Log.error("protocol error: no transaction number recieved " + elems)
          None
        }
    }
    message
  }
}
