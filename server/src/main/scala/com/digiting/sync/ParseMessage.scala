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

object ParseMessage {
  /** parses a string containing a json transaction message from the client.  */
  def parse(receivedXact:String):Option[JsonMessage] = {
//    Log.info("ParseMessage.receiveTransaction " + receivedXact)
    var message:Option[JsonMessage] = None
    
	// extract the transaction number object and the transaction body objects
	JSONParser.parse(receivedXact) match {
	  case Full(json) => 
        processJson(json) 
          {jsonArray => 
            message = parseJsonMessageBody(jsonArray)
          } 
          {jsonObj => Log.error("protocol error: sync messages should start be enclosed in json array []:\n" + receivedXact);}
	  case _ => 
	    Log.info("hard to parse transaction received: " + receivedXact)	           
	}    
    message
  }          
  
  /** pull out the parts of the json transaction message  */
  private def parseJsonMessageBody(elems:List[_]):Option[JsonMessage] = {
    var xactNumber:Option[Int] = None
    val edits = new collection.mutable.ListBuffer[Map[String,Any]]
    val syncs = new collection.mutable.ListBuffer[Map[String,Any]]
    var reconnecting = false  // if the client is continuing a sync session (otherwise we reset to a fresh state)

    // parse the json string into json objects, separating out control messages e.g. #transaction
    elems foreach {json => processJson(json)
      {jsonArray => Log.error("protocol error: sync protocol messages should not have nested json arrays")}
      {jsonObj => {
        { jsonObj get "#transaction" map {number => xactNumber = Some(jsonValueToInt(number))}              
        } orElse {
          jsonObj get "#edit" map {_ => edits + jsonObj; Some(jsonObj)}               
        } orElse {
          jsonObj get "#reconnect" map {b => reconnecting = b.asInstanceOf[Boolean]; Some(reconnecting)}               
        } orElse {
          jsonObj get "id" map {_ => syncs + jsonObj; Some(jsonObj)}
        } orElse {
          Log.error("protocol error: unexpected object received: " + jsonObj); None
        }
       ""
      }}
    }
    
    import JsonMessageControl._
    val jsonMessage = xactNumber match { 
      case Some(num) =>
        var control:JsonMessageControl.Value = Init
        if (reconnecting)
          control = Continue 
        Some(new JsonMessage(control, num, edits.toList, syncs.toList))
      case None => 
        Log.error("protocol error: no transaction number recieved " + elems)
        None
    }
    jsonMessage
  }
}
