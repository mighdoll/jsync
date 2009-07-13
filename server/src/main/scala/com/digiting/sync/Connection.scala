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

import SendBuffer._
import _root_.net.liftweb.util._
import net.liftweb.util.JSONParser
import collection._
import com.digiting.sync.aspects.Observable
import JsonUtil._

/**
 * Manages a connection to a client using the json sync protocol.
 */
class Connection(val connectionId:String) {  
  val sendBuffer = new SendBuffer						// buffers messages to go to the client
  val receiver = new Receiver(this)					 	// processes messages from the client     
  val subscribes = new ActiveSubscriptions(this)		// active subscriptions from the client
  val connectionPartition = new RamPartition(connectionId)
  val defaultPartition = new RamPartition("user") 		// we'll create new client objects here, TODO make this a per user persistent partition 
    
    // when the receiver's visible set commits, send changes to the client too
  receiver.watchCommit(sendPendingChanges)
    
  /** send any queued model changes to the client in a single transaction 
   * 
   * (Note that this may be called from an arbitrary thread)
   */
  private def sendPendingChanges(changes:Seq[ChangeDescription]) = {
    val pending = subscribes.takePending()
    if (!pending.isEmpty) {
      var json = Message.makeMessage(pending)
//      Console println "JsonConnction: queueing Pending Change: " + json.toJson
      sendBuffer ! QueueMessage(json)
    } else {
//      Log.info("sendPendingChanges: nothing Pending")
    }
  }
}

