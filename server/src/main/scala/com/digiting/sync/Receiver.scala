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
import JsonUtil._
import actors.Actor._
import actors.Actor
import scala.util.DynamicVariable
import net.lag.logging.Logger
import net.lag.logging.Level._


object Receiver {
  case class ReceiveMessage(message:Message)
}

/** 
 * Drives the updating of syncable objects and collections from messages
 * received over the wire.  
 * 
 * Queues messages received out of order.  
 * 
 */
class Receiver(connection:Connection) extends Actor {
  var receivedXact:Int = -1  				// last sequence number we've received from the client 
  val receivedMessages = new mutable.HashSet[Message]  // queue of messages received out of order
  val messageSequenceTimeout = 5000 		// milliseconds to wait for an out of order message
  var lastProcessed:Long = System.currentTimeMillis	// last time we received a message.
  val receiverId = System.currentTimeMillis // debug id 
  val log = Logger("Receiver")

  start
  
  import Receiver._
  def act = {	
    loop {
      react {
        case ReceiveMessage(message) => receiveMessage(message)
        case m => log.error("Receiver: unexpected actor message: %s", m)
      }
    }
  }
  
  /** queue one sync message received from the client possibly received out of order */
  private def receiveMessage(message:Message) = {
    log.trace("#%s  receiveMessage: %s", connection.debugId, message.toJson)
    receivedMessages += message
    processReceived
  }
  
  /** see if we have the next protocol message  */
  private def processReceived:Unit = {
    // get any ready messages
    val msgOpt = receivedMessages find {message => message.xactNumber == receivedXact + 1 }    
    msgOpt match { 
      case None =>
        log.trace("processReceived - skipping, waiting for: %s", receivedXact + 1)
        if (System.currentTimeMillis - lastProcessed > messageSequenceTimeout) {
          log.warning("timeout waiting for out order client message, transaction: %d.  restarting connection", receivedXact + 1)
          // reset
          // LATER send reset to client if we're connected
        }
      case Some(message) =>        
        connection.appContext match {
          case Some(app) =>
            app.processMessage.process(message)	// CONSIDER actorify appContext? 
          case _ =>
            log.error("processReceived() - no app defined!  how can I process message: %s", message.toJson)
        }
        lastProcessed = System.currentTimeMillis
        receivedMessages - message
        receivedXact = message.xactNumber
        if (receivedMessages.size > 0)
          processReceived      // process some more messages if we can
    }
  }  
}

