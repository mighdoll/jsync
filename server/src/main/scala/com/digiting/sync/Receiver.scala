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
import JsonUtil._
import actors.Actor._
import actors.Actor
import scala.util.DynamicVariable
import SyncManager.NewSyncableIdentity

object Receiver {
  case class ReceiveMessage(message:Message)
}

/** 
 * Drives the updating of syncable objects and collections from messages
 * received over the wire.  
 * 
 * Queues messages received out of order.  
 * 
 * Drives application processing loop:
 *   fetch message objects from partitions
 *   update message objects
 *   send application notifiations 
 *   commit changes to partitions
 *     (Connection watches and queues change messages back to the client)
 */
class Receiver(connection:Connection) extends Actor {
  var receivedXact:Int = -1  				// last sequence number we've received from the client 
  val receivedMessages = new mutable.HashSet[Message]  // queue of messages received out of order
  val messageSequenceTimeout = 5000 		// milliseconds to wait for an out of order message
  var lastProcessed:Long = System.currentTimeMillis	// last time we received a message.
  val receiverId = System.currentTimeMillis // debug id  

  start
  
  import Receiver._
  def act = {	
    loop {
      react {
        case ReceiveMessage(message) => receiveMessage(message)
        case m => Log.error("Receiver: unexpected actor message: " + m)
      }
    }
  }
  
  /** queue one sync message received from the client possibly received out of order */
  private def receiveMessage(message:Message) = {
    Console println("Receiver.receive: " + message.toJson)
    receivedMessages += message
    processReceived
  }
  
  /** see if we have the next protocol message  */
  private def processReceived:Unit = {
    // get any ready messages
    val msg_ = receivedMessages find {message => message.xactNumber == receivedXact + 1 }    
    msg_ match { 
      case None =>
//        Console println ("processReceived - skipping, waiting for: " + (receivedXact + 1))
        if (System.currentTimeMillis - lastProcessed > messageSequenceTimeout) {
          Log.warn("timeout waiting for out order client message, transaction:" + (receivedXact + 1) + ".  restarting connection")
          // reset
          // LATER send reset to client if we're connected
        }
      case Some(message) =>        
        processMessage(message)
        lastProcessed = System.currentTimeMillis
        receivedMessages - message
        receivedXact = message.xactNumber
        processReceived      // recurse to process some more messages if we can
    }
  }
  
  /** process one incoming message 
    * This is the key application processing loop, SOON give it more prominence in the source code.   */
  private def processMessage(message:Message) = {    
    Log.info("Receiver(" + connection.connectionId + ").processMessage: " + message.toJson)
    try {
      Observers.currentMutator.withValue(connection.connectionId) {
	  	processSyncs(message.syncs)
		processEdits(message.edits)
      }
      
      Observers.currentMutator.withValue("server-application") {
      // TODO release notifications and call application code (TODO)        
      }
     

      // commit changes to disk and network
	  commit()
    } catch {
      // LATER send client a protocol reset message, reset our state too
      case e:Exception => Log.error("exception procesing request: " + e + e.getStackTraceString)
    }
  } 
  
  
  /** LATER the idea is that later this use should use Software Transactional Memory on the sync manager's data set */
  def commit() {    
    SyncManager.instanceCache.commit();
  }
  
  /** LATER make this use STM, but for now just feed through to the sync manager */
  def watchCommit(func:(Seq[ChangeDescription])=>Unit) {
  	SyncManager.instanceCache.watchCommit(func)
  }
  
  /** process changes to syncable collections */
  private def processEdits(elems:List[Map[String,Any]]):Unit = {
    for (edit <- elems) {
      val editOpt = edit get "#edit"
      editOpt match {
        case Some(JsonTarget(target)) => processOneEdit(target, edit) 
        case _ => Log.error("processEdits can't find target of #edit: " + edit)
      }
    }
  }
  
  /** extract the local syncable referenced by a an id, partitionId pair in a jsonMap */
  object JsonTarget {
    def unapply(target:Any):Option[Syncable] = {
      target match {
        case map:Map[_,_] => 
          findLocalSyncable(map.asInstanceOf[Map[String,Any]])        
        case _ => None
      }
    }
  }
  
  /** handle one edit operation in the protocol */
  private def processOneEdit(target:Syncable, edit:Map[String,Any]) {
    edit match {
      case EditPut(puts:List[_]) => processPut(target, puts)
      case EditPut(putOne:Map[_,_]) => processPut(target, putOne :: Nil)
      case EditClear(clear:Boolean) => processClear(target)
      case _ => Log.error("Reciever: unexpected #edit " + edit)
    }
  }
  
  object EditPut {
    def unapply(edit:Map[String,Any]):Option[Any] = {
      edit get "put"
    }
  }
  
  object EditClear {
    def unapply(edit:Map[String,Any]):Option[Boolean] = {
      edit get "clear" match {
        case Some(true) => Some(true)
        case _ => 
          Log.error("Unexpected clear value in map: " + edit)
          None
      }
    }
  }
  
  /** handle the #edit.clear operation in the protocol */
  private def processClear(target:Syncable) {
    target match {
      case set:SyncableSet[_] => set.clear()
      case _ => Log.error("processClear against something other than a SyncableSet: " + target)
    }  
  }
  
  /** handle the #edit.put operation in the protocol */
  private def processPut(target:Syncable, puts:List[_]) {
    target match {
      case set:SyncableSet[_]  => 
        for (putRef <- puts) {
          putRef match {		// CONSIDER using JsonTarget here?
            case json:Map[_,_] => 
              findLocalSyncable(json.asInstanceOf[Map[String,Any]]) map {
                toAdd => 
                set.asInstanceOf[SyncableSet[Syncable]] += toAdd
              }
            case _ => Log.error("processPut: unexpected element in a put: " + putRef)
          }
        }
      case _ => Log.error("processPut: expected target of a #edit.put to be a SyncableSet, but it's a: " + target)
    }
  }
  

  /* process a set of incoming jsync elements.  Received elements are  
   * @param json  collection of parsed json/jsync elements each */
  private def processSyncs(elems:List[Map[String,Any]]) = {        
    elems foreach (json => {
      val objOpt:Option[Syncable] = updateObj(json)	// update the scala object with json data	
    
      // check for subscription objects (SOON do this elsewhere, and trigger off changes to the #subscriptions object?)
      objOpt foreach (obj => {
        if (obj.kind == "$sync.subscription") 
          connection.subscribes.subscribe(obj.asInstanceOf[Subscription])                
      })
    })
  }

  /** Create or update a Syncable object from a json/jsync object or fragment.
   * If the object is known to the sync manager, it is updated to reflect the
   * received data, otherwise a new object is created.
   * 
   * @remote  json object or fragment to update
   * @return  the Syncable object found or created
   */
  private def updateObj(remote:Map[String, Any]):Option[Syncable] = {
    localTarget(remote) map {local => 
      updateLocal(local, remote) 
      local
    }
  }
  
  /** find or create a local Syncable to match the received protcol object */
  private def localTarget(remote:Map[String, Any]):Option[Syncable] = {
    getIds(remote) match {
      case Some(ids) =>
        val kindOpt = toSomeString (remote get "kind")
        kindOpt match {	// sending '$kind' is our protocol clue to build a new object
          case Some(kind) =>
            SyncManager.newSyncable(kind, ids)          
          case None =>
            SyncManager.get(ids)
        }
      case None =>  None
    }
  }
  
  /** find an existing syncable with the ids specified in the local map */
  private def findLocalSyncable(json:Map[String, Any]):Option[Syncable] = {
    for (ids <- getIds(json); syncable <- SyncManager.get(ids)) 
      yield {
        verifyKindsMatch(syncable, json)
        syncable 
      }  
  }
  
  /** get the instance and partition ids out of map.  If the partitionId isn't
    * specified, use the connections default partition.  */
  private def getIds(json:Map[String, Any]):Option[NewSyncableIdentity] = {    
    val foundIds:Option[NewSyncableIdentity] = 
    toSomeString (json get "id") match { 
      case Some(instanceId) => {
        toSomeString (json get "$partition") match {
          case Some(partitionId) => 
            Partitions get partitionId match {
              case Some(partition) => Some(NewSyncableIdentity(instanceId, partition))
              case None => None
            } 
          case None => 
            Some(NewSyncableIdentity(instanceId, connection.defaultPartition))
        }         
      } 
      case None => None
    }
      
    foundIds orElse {
      Log.error("Receiver.getIds() - unable to parse ids in map: " + json)
      None
    }
  }
    
  
  /** update a local object with fields from a received protocol object */
  private def updateLocal(local:Syncable, remote:Map[String, Any]) {
    val setProperties = remote filter {
      case(name,value) => !SyncableInfo.isReserved(name)
    }
    val sanitized:Iterable[(String,AnyRef)] = for ((name,value) <- setProperties)
      yield (name, sanitizeJsonValue(value))

    local match {
      case json:SyncableJson => 
        Log.error("client objects without a corresponding server class.  NYI")
        throw new NotYetImplemented
      case syncable:Syncable =>        
//      Console println (sanitized mkString("sanitized Fields:", ",", ""))
        verifyKindsMatch(local, remote)
        SyncManager.copyFields(sanitized, local)
    }
  }
  
  /** debug verification that the json kind matches the object */
  private def verifyKindsMatch(local:Syncable, remote:Map[String,Any]) {
    for (remoteKind <- toSomeString (remote get "kind")) {
      if (remoteKind != local.kind)
        Log.error("local syncable kind (" +local.kind + ") does not match remote kind (" + remoteKind + ")")
    }
  }
      
  /** cast to an Option[String] */
  private def toSomeString(option: Option[Any]):Option[String] =
    option flatMap (_ match { case s:String => Some(s) })  
}

