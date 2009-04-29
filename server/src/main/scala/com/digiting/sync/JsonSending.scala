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

object JsonSendBuffer {
  case class QueueMessage(message:JsonMessage)  
  case class TakeOrEmpty()  // return queued json transaction, 
  case class Take() 
  case class Pending(json:String)
  case class Reset()   
}

import JsonSendBuffer._

/* Buffers messages to be sent to the client */
class JsonSendBuffer extends Actor {
  var nextXactNum:Int = 0
  val pending = new mutable.Queue[String] // json-sync transactions to go to the client
  val takers = new mutable.Queue[OutputChannel[Any]]

  /* Receive protocol messages ready to be serialized and sent to the client.
   * Protocol message sequence numbers are set in the order that message are received.  */
  def act = {
    loop {
      react {
        case QueueMessage(message) =>
//          Console println "SendBuffer queuing: " + message
          message.xactNumber = nextXactNum
          nextXactNum += 1
          pending += message.toJson
          notifyTakers
        case TakeOrEmpty() => 
          reply(takePending)
        case Take() => 
//          Console println "SendBuffer Take: " + sender
          takeWhenReady(sender)
          if (!pending.isEmpty)
            notifyTakers
        case Reset() =>
//          Console println "SendBuffer reset " 
          pending.clear
        case unexpected => 
          Log.error("unexpected message received: " + unexpected)
      }
    }
  }
  
  def takePending():Pending = {
    val json =           
      if (pending.isEmpty)
        "[]"
      else 
        pending.mkString("", ",", "")
    
    pending.clear           
    Pending(json)
  }
  
  def notifyTakers {
    val taker = takers.dequeue
//    Console println "notifyTaker: " + taker
    taker ! takePending
  }
  
  def takeWhenReady(receiver:OutputChannel[Any]) {
    takers += receiver
  }
}
  