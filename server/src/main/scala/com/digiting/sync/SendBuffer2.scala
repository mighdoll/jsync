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
import collection.mutable
import net.lag.logging.Logger
import actors.Actor
import actors.Actor.loop
import scala.actors.OutputChannel  
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.MILLISECONDS
import scala.collection.mutable.ListBuffer


object PutSendBuffer {
  /* Receive a protocol messages ready to be serialized and sent to the client.
   * Protocol message sequence numbers are set in the order that message are received.  
   */
  case class Put(message:Message)
  
  /* like Put, but sends a boolean reply when complete, so that it can be used
   * synchronously */
  case class PutAndReply(message:Message)
}

object TakeSendBuffer {
  /** remove all messages queued for sending.  
    * replies with an Option[String] of all json message strings, 
    *   wrapped in a json array; or None if none is available 
    * 
    * @param until - expire take request at this many msec since the epoch
    */
  case class TakeUntil(until:Long)
  
  /** remove all messages queued for sending.  
    * replies with an Option[String] of all json message strings, 
    *   wrapped in a json array; */
  case class Take()
  
  case class TakeResponse(val responseOpt:Option[String])
}

import TakeSendBuffer._
import PutSendBuffer._

class TakeSendBuffer(debugId:Int, queue:BlockingQueue[String]) extends Actor {  
  val log = Logger("TakeSendBuffer")
  
  start
  def act() {
    loop {
      react {
        case TakeUntil(until) =>
          val rawTimeout = until - System.currentTimeMillis
          val timeout = if (rawTimeout <= 0) 1L else rawTimeout 
          log.trace("TakeUntil() #%d, timeout: %d", debugId, timeout)
          val first = queue.poll(timeout, MILLISECONDS)
          val response = 
            if (first != null)
              Some(drainMessages(first))
            else 
              None
          sender ! TakeResponse(response)
        case Take() =>
          log.trace("Take() #%d", debugId)
          val messages = drainMessages(queue.take)
          sender ! TakeResponse(Some(messages))
        case x => log.error("unexpected request: %s", x)
      }
    }    
  }
  
  /** combine the first message with any available remaining messages and wrap 'em up
   * in a json array.
   * 
   * @param first - a json object for the first object in the message
   */
  private def drainMessages(first:String):String = {
    val messages = new ListBuffer[String]
    messages += first
    while (queue.size() > 0) {
      queue.poll() match {
        case null =>
        case elem => messages += elem
      }
    }
    val result = messages.mkString("[", ",", "]")	
    log.ifTrace("returning #" +  debugId + " : " + result.lines.take(1).mkString)
    result
  }  
}

/** Holds a set of protocol messages destined for a client.  
  * 
  * Threads sending to the client drop in messages via put() and protocol
  * sender threads fetch ready messages via take()
  * 
  * Messages are numbered in the order they're received, so the client
  * sees a unified sequence.  
  * 
  */
class PutSendBuffer(val debugId:Int) extends Actor {
  val log = Logger("PutSendBuffer")
  var nextXactNum:Int = 0
  val pending = new LinkedBlockingQueue[String]
  val take = new TakeSendBuffer(debugId, pending)

  start
  def act() {
    loop {
      react {
        case Put(message) => put(message)
        case PutAndReply(message) => 
          put(message)
          sender ! true
        case x => log.error("unexpected request: %s", x)
      }
    }
  }
  
   /* Receive protocol messages ready to be serialized and sent to the client.
   * Protocol message sequence numbers are set in the order that message are received.  */
  private def put(message:Message) = {
    message.xactNumber = nextXactNum
    nextXactNum += 1
    log.ifTrace("putting message in buffer: " + message.toJson)
    pending.offer(message.toJson)
  }

}
