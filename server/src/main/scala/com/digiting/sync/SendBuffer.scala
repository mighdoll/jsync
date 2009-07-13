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
import actors._
import actors.Actor._
import collection._

object SendBuffer {
  case class QueueMessage(message:Message)  
  case class TakeOrEmpty()  // return queued json transaction, 
  case class Take() 
  case class Pending(json:String)
}

import SendBuffer._

// 
// CONSIDER using java.util.concurrent..
// "use actors when you want queues/asynchonous and pattern matching"
//

/** Buffer of Message objects to be sent to the client */
class SendBuffer extends Actor {	
  var nextXactNum:Int = 0
  val pending = new mutable.Queue[String] // json-sync transactions to go to the client
  val takers = new mutable.Queue[OutputChannel[Any]]

  start
  
  /* Receive protocol messages ready to be serialized and sent to the client.
   * Protocol message sequence numbers are set in the order that message are received.  */
  def act = {
    loop {
      react {
        case QueueMessage(message) =>
//          Console println "SendBuffer queueing: " + message.toJson
          queueMessage(message)
          checkTakers
        case TakeOrEmpty() => 
          reply(takePending)
        case Take() => 
//          Console println "SendBuffer Take: " + sender
          queueTaker(sender)
          checkTakers
          pending.clear
        case unexpected => 
          Log.error("unexpected message received: " + unexpected)
      }
    }
  }
  
  /** call this only if you're sure that the actor isn't receiving messages yet */
  def unsafeQueueMessage(message:Message) = queueMessage(message)
  
  private def queueMessage(message:Message) {
    message.xactNumber = nextXactNum
    nextXactNum += 1
    pending += message.toJson
  }

  /** remove all messages queued for sending.  
    * @return pending json message strings, wrapped in a json array; or an empty array */
  def takePending():Pending = {
    val json = {          
      if (pending.isEmpty)
        "[]"
      else 
        pending.mkString("[", ",", "]")	
    }
//    Log.info("sendBuffer.takePending: " + json)
    
    pending.clear
    Pending(json)
  }
  
  private def checkTakers {
    if (!takers.isEmpty && !pending.isEmpty) {
	  takers.dequeue ! takePending
    }
  }
  
  private def queueTaker(receiver:OutputChannel[Any]) {
    takers += receiver
  }
}
  