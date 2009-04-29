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
import _root_.net.liftweb.util._
import com.digiting.sync.syncable._
import com.digiting.sync.aspects.Observable
import Observation._
import JsonUtil._
import actors.Actor._
import actors.Actor
import JsonMessageControl._


class JsonReceiving(connection:JsonConnection) {
  var receivedXact:Int = -1
  var sentXact:Int = 0	// last sequence number we've sent to client
  val receivedMessages = new mutable.HashSet[JsonMessage]
  var lastProcessed:Long = System.currentTimeMillis
  val receiverId = System.currentTimeMillis

  def reset = 
    receivedXact = -1
    sentXact = 0
    lastProcessed = System.currentTimeMillis
    
  /** queue one sync message received from the client possibly received out of order */
  def receive(message:JsonMessage) = {
    Console println ("JsonReceiving.receive: " + message + " into: " + receiverId)
    receivedMessages += message
    processReceived
  }
  
  /** see if we have the next protocol message  */
  private def processReceived:Unit = {
    val messageSequenceTimeout = 5000 // milliseconds to wait for an out of order message
    // get any ready messages
    val msg_ = receivedMessages find {message => message.xactNumber == receivedXact + 1 }    
    msg_ match { 
      case None =>
//        Console println ("processReceived - skipping, waiting for: " + (receivedXact + 1))
        if (System.currentTimeMillis - lastProcessed > messageSequenceTimeout) {
          Log.warn("timout waiting for out order client message, transaction:" + (receivedXact + 1) + ".  restarting connection")
          // reset
          // TODO send reset to client if we're connected
        }
      case Some(message) =>        
        processMessage(message)
        lastProcessed = System.currentTimeMillis
        receivedMessages - message
        receivedXact = message.xactNumber
        processReceived      // recurse to process some more messages if we can
    }
  }
  
  /** process one incoming message */
  private def processMessage(message:JsonMessage) = {
    Log.info("jsonReceiving.processMessage: " + message)
    // treat this as a fresh client connection
    if (message.control == Init) {
      // TODO reset subscriptions, etc.
    }
    
    try {
	    processSyncs(message.syncs)
	    processEdits(message.edits)
    } catch {
      // TODO send client a protocol reset message, reset our state too
      case e:Exception => Log.error("exception procesing request: " + e + e.getStackTraceString)
    }
    
    // automatically commit server transaction
    SyncManager.commit()
  } 
    
  private def processEdits(elems:List[Map[String,Any]]) = {
    // TODO process #edits to collections
    if (!elems.isEmpty)
      throw new NotYetImplemented
  }

    /* process a set of incoming jsync elements.  Received elements are  
     * @param json  collection of parsed json/jsync elements each */
    private def processSyncs(elems:List[Map[String,Any]]) = {        
      elems foreach (json => {
        val objX:Option[Syncable] = updateObj(json)	// update the scala object with json data
      
        // check for subscription objects (LATER do this elsewhere)
        objX foreach (obj => {
          if (obj.kind == "$sync.subscription") 
            connection.subscribe(obj.asInstanceOf[SubscriptionRoot])                
        })
      }) 
    }

/* create or update a Syncable object from a json/jsync object or fragment.
 * If the object is known to the sync manager, it is updated to reflect the
 * received data, otherwise a new object is created.
 * 
 * TODO use the protocol to decide whether to create or update 
 * (perhaps whether the 'kind' field is present.  The client knows whether 
 * it's creating or updating..)
 * 
 * @remote  json object or fragment to update
 * @return  the Syncable object found or created
 */
  private def updateObj(remote:Map[String, Any]):Option[Syncable] = {
    
    /* cast to an Option[String] */
    def toSomeString(opt: Option[Any]):Option[String] =
      opt flatMap (_ match { case s:String => Some(s) })

    /* contains a syncable identifier */
    case class SyncRef (id:String, kind:String)
    
    val id = toSomeString (remote get "id")
    val kind = toSomeString (remote get "kind")
    
    val ref = for (i <- id; k <- kind) yield (SyncRef(i,k))
    ref match {
        case Some(s) => {
            val local = SyncManager.getOrMake(s.id, s.kind)
            val setProperties = remote filter {
              case(name,value) => !SyncableInfo.isReserved(name)
            }
            val sanitized:Iterable[(String,AnyRef)] = for ((name,value) <- setProperties)
              yield (name, sanitizeJsonValue(value))
            
            SyncManager.copyFields(sanitized, local)
            Some(local)
        }
        case None =>
		    if (!id.isDefined)
		        Log.error("protocol error: no id in obj: " + remote)
		    else if (!kind.isDefined)
		        Log.error("protocol error: no kind in obj: " + remote)
		    else
		        Log.error("protocol error: shouldn't get here...: " + remote)
		    None
    }
}
    
}
