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

import JsonSendBuffer._
import _root_.net.liftweb.util._
import net.liftweb.util.JSONParser
import com.digiting.sync.syncable.SubscriptionRoot
import collection._
import com.digiting.sync.aspects.Observable
import Observation._
import JsonUtil._
import actors.Actor._
import actors.Actor

object JsonConnection {
  case class ReceiveJsonText(text:String)
}
import JsonConnection._
class JsonConnection extends Actor {
  
  val sendBuffer = new JsonSendBuffer
  val jsonReceiver = new JsonReceiving(this)
  val pendingChanges = new mutable.Queue[ChangeDescription]  // syncable model changes waiting for a commit

  
  // when the SyncManger commits, send changes to the client too
  SyncManager.watchCommit(sendPendingChanges)
  sendBuffer.start
  start
          
  def reset = {
    Log.info("JsonConnection.reset")
    sendBuffer ! Reset()
    jsonReceiver.reset
  }
  
  def act() {
    loop {
      react {
        case ReceiveJsonText(receivedText) => {
          val parsed = ParseMessage.parse(receivedText) 
          parsed match {
            case Some(message) => jsonReceiver.receive(message)
            case None =>
          }
        }
      }
    }
  }

  /** send any queued model changes to the client in a single transaction */
  private def sendPendingChanges() = {
    var json = MakeMessage.constructTransaction(pendingChanges)
//    Console println "JsonConnction: queueing Pending Change: " + json.toJson
    sendBuffer ! QueueMessage(json)
  }
//  
//  /** Fetch a completed transaction destined for the client. */
//  def takePending():String = {
//    val json = sendBuffer.takePending getOrElse("[]")
//    Log.info("retrieveToClient: " + json)
//    json
//  }
  
  def subscribe(subscription:SubscriptionRoot) = {
    Subscriptions.subscribe(subscription.name) match {
      case Some(root) => 
        Observation.watchDeep(subscription, queueChanges, "SyncManager")
//          Console println "setting subscription root to: " + root + " on subscription: " + subscription
        subscription.root = root;
      case _ =>
        Log.warn("unknown subscription name: " + subscription.name +" in subscription: " + subscription)
    }
  }
  
  /** remember a change that we'll later send to the client */
  def queueChanges(obj:Observable, change:ChangeDescription):Unit = {      
    pendingChanges += change
    Console println "JsonSync change queued: " + change      
  }
}

