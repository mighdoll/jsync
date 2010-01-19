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

import com.digiting.sync.aspects.Observable
import net.lag.logging.Logger
import com.digiting.sync.ResponseManager.AwaitResponse

object ConnectionDebug {
  var nextId = -1
  def nextDebugId():Int = {nextId += 1; nextId}
}

/**
 * Manages a connection to a client using the json sync protocol.
 */
class Connection(val connectionId:String) {
  val log = Logger("Connection")
  val debugId = ConnectionDebug.nextDebugId()
  val putSendBuffer = new PutSendBuffer(debugId)		// put interface to buffer messages to go to the client
  val takeSendBuffer = putSendBuffer.take				// take interface for messages to go to the client
  val receiver = new Receiver(this)					 	// processes messages from the client     
  var appContext:Option[AppContext] = None				// application handling this connection
  val responses = new ResponseManager(takeSendBuffer)
  
  def close() {
    log.info("close() #%d", debugId)
    putSendBuffer
  }
  
  def takeResponse():Option[String] = {
    val response = responses !? AwaitResponse(debugId)
    log.trace("takeResponse() received %s", response)
    Some(response.asInstanceOf[String])
  }
  
}

