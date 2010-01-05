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

import JsonUtil._
import collection._
import net.lag.logging.Logger
import net.liftweb.util.JSONParser
import net.liftweb.util.Full

/**
 * Utility routine to parse a Message from a json string.
 */
object ParseMessage {
  val log = Logger("ParseMessage")
  
  /** parses a string containing one or more json transaction messages from the client.  */
  def parse(receivedXact:String):List[Message] = {
    log.ifTrace("ParseMessage.receiveTransaction " + receivedXact)
    
	// extract the transaction number object and the transaction body objects
	JSONParser.parse(receivedXact) match {
      case Full(jsonArray:List[_]) => 
        jsonArray head match {
          case jsonObj:Map[_,_] => 
            // old protocol rev -- one transaction message array per string.  SOON get rid of this
            val message = parseJsonMessageBody(jsonArray) 
            message toList
          case multi:List[_] =>
            jsonArray flatMap {list => 
              parseJsonMessageBody(list.asInstanceOf[List[_]]) toList
            }
        }
	  case _ => 
	    log.error("hard to parse transaction received: " + receivedXact)
	    Nil
	}
  }  

  object ProtocolTransaction {
    def unapply(json:Map[String,Any]):Option[Int] = {
      for (num <- json get "#transaction")
        yield jsonValueToInt(num)           
    }
  }
  
  object ProtocolEdit {
    def unapply(json:Map[String,Any]):Option[Map[String,Any]] = {
       json get "#edit" match {
         case Some(m:Map[_,_]) => Some(json)
         case Some(b) => log.error("unexpected value of #edit: "  + json); None
         case _ => None
       }
    }
  }
  
  object ProtocolControl {
    def unapply(json:Map[String,Any]):Option[Map[String,Any]] = {
      json find { 
    	case(name:String,value) => name.charAt(0) == '#'
        } map { _ => 
          json  
       }
    }
  }
  
  object ProtocolSync {
    def unapply(json:Map[String,Any]):Option[Map[String,Any]] = {
	  json get "id" map {_ => json}
    } 
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
      {jsonArray => jsonArray}
      {jsonObj => jsonObj match {
        case ProtocolTransaction(num) => xactNumber = Some(num)
        case ProtocolEdit(edit) => edits + edit
        case ProtocolControl(ctl) => controls + ctl
        case ProtocolSync(sync) => syncs + sync
        case _ =>
          log.error("protocol error: unexpected object received: " + jsonObj); None
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
            log.error("protocol error: no transaction number recieved " + elems)
            None
          }  
        } else {
          log.error("protocol error: no transaction number recieved " + elems)
          None
        }
    }
    message
  }
}
