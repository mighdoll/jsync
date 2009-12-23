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

import actors.Actor._
import actors.Actor
import net.lag.logging.Logger
import scala.collection.mutable.Queue
import scala.actors.OutputChannel  
import TakeSendBuffer._

object ResponseManager {
  /** sends a string containing the response to the sender */
  case class AwaitResponse(debugId:Int)
}

import ResponseManager._
/** 
  * Organizes a set of actors requesting a response from the SendBuffer 
  * 
  * The client is responsible for ensuring that there is always 
  * an outstanding request or two.  The server prunes excess requests.
  * 
  * Requests wait no longer than requestTimeout, 15sec by default, plus
  * a little processing time.  
  * 
  * If no response is available, an empty response is sent.
  * 
  * If extra requests (beyond maxWaiters, which defaults to one) are made, 
  * an empty response is sent to the excess requests.  
  */
class ResponseManager(takeSendBuffer:TakeSendBuffer) extends Actor {
  // TODO show debugId for logging 
	/* Connections waiting for data, sort most recent first */
	case class AwaitingResponse(val requestor:OutputChannel[Any], val debugId:Int) {
	  val waitStart = System.currentTimeMillis
	}

  val maxWaiters = 1
  val requestTimeout = 15000 
  val waiters = new Queue[AwaitingResponse]()  
  val log = Logger("ResponseManager")
  var waitingForSendBuffer = false

  start
  def act() {
    loop {
      react {
        case ResponseManager.AwaitResponse(debugId) => 
          log.trace("AwaitResponse() #%d", debugId)
          waiters += AwaitingResponse(sender, debugId)
          sendEmptyResponse(maxWaiters)  // prune excess waiters
          waitForSendBuffer(false)
        case TakeSendBuffer.TakeResponse(Some(response:String)) =>  
          log.ifTrace("TakeResponse() received: " + response.lines.take(1).mkString)
          responseToWaiter(response)
          waitForSendBuffer(true)
        case TakeSendBuffer.TakeResponse(None) => 
          log.trace("TakeResponse() received: None")
          responseToWaiter("[]")
          waitForSendBuffer(true)
        case x => log.error("unexpected message: %s", x)
      }
    }
  }

  /* send one take request to to the TakeSendBuffer if appropriate.
   * 
   * only one take request should be outstanding at any time, lest
   * a second response arrive after we've pruned the requestor
   */
  private def waitForSendBuffer(resetWait:Boolean) {
    if (resetWait) 
      waitingForSendBuffer = false
    
    if (!waiters.isEmpty && !waitingForSendBuffer) {
      waitingForSendBuffer = true
      val now = System.currentTimeMillis 
      val until = now + requestTimeout - (now - waiters.first.waitStart)

      log.trace("waitForSendBuffer() requesting from takeSendBuffer until: %d", until)
      takeSendBuffer ! TakeSendBuffer.TakeUntil(until)
    } else {
      log.trace("waitForSendBuffer() no Awaits pending, not waiting on TakeSendBuffer")
    }
  }
  
  private def responseToWaiter(response:String) {
    val waiter = waiters.dequeue()  
    waiter.requestor ! response
  }
  
  /** send empty responses to extraneous waiters */
  private def sendEmptyResponse(preserve:Int) {
    if (preserve < 1) 
      log.warning("preserving only %d waiters?", preserve)    
    
    while (waiters.size > preserve) {
      val waiter = waiters.dequeue 
      log.trace("pruning extraneous pending request")
      waiter.requestor ! "[]"
    }    
  }
}

