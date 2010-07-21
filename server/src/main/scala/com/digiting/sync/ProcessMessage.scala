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
import net.lag.logging.Logger
import net.lag.logging.Level._
import com.digiting.sync.syncable._
import JsonUtil._
import com.digiting.util._
import collection.mutable.ListBuffer
import JsonMapParser._
import com.digiting.sync.JsonObject.JsonMap
import Function.tupled
import com.digiting.util.TryCast.matchOptString

/** this the core routine that reacts to a transaction of object changes from a remote client. 
 * 1) Fetch objects in the transaction from partitions so that we processcan operate on them 
 * 2) make client mutations as per the client's message 
 * 3) notify server applications of the changes (apps may make further mutations), 
 * 4) commit changes to storage and to listening clients 
 * 5) AppContext watches commit() too, and queues change messages back to the client
 */
object ProcessMessage extends LogHelper {
  val log = Logger("ProcessMessage")
    
  
  /** process one incoming client transaction */
  def process(message:Message, app:AppContext) = {    
    log.logLazy(TRACE, message.toJson)
    app.withApp {
      try {
        // apply modifications in the transaction to all objects as a group, before notifying app
        Observers.pauseNotification {
          Observers.currentMutator.withValue(app.connection.connectionId) {
            val references = ReferencePatching.collectReferences {
              processSyncs(message.syncs) 
            }
            patchReferences(references)
            processEdits(message.edits)
            
            
            // first release notifications so the partition sees the objects before any observe on them
            Observers.releasePaused {_ == app.instanceCache}
            
            // second release notifications to DeepWatch early so that new DeepWatch Changes will have the client connection as the mutator
            Observers.releasePaused {_.isInstanceOf[DeepWatch]}
          }
        }
        app.notifyWatchers()
        ; // third, release notifications to the app.  Responses are processed in app context 
      } catch {
        // LATER send client a protocol reset message, reset our state too
        case e:Exception => log.error(e, "exception procesing request")
      } 
    }
  } 
  
  private def patchReferences(references:Seq[ReferencePatch]) {
    for {
      patch <- references
      access = SyncManager.kinds.propertyAccessor(patch.referer)
      target <- App.app.get(patch.targetId)
    } {
      log.trace("patching reference: %s.%s = %s", patch.referer, patch.field, target)
      access.set(patch.referer, patch.field, target)
    }
  }
  
  /** process changes to syncable collections */
  private def processEdits(elems:List[JsonMap]):Unit = {
    for (edit <- elems) {
      val editOpt = edit get "#edit"
      editOpt match {
        case Some(JsonTarget(target)) => processOneEdit(target, edit) 
        case _ => log.error("processEdits can't find target of #edit: %s", edit)
      }
    }
  }
  
  /** extract the local syncable referenced by a an id, partitionId pair in a jsonMap */
  object JsonTarget extends LogHelper {
    val log = Logger("JsonTarget")
    def unapply(target:AnyRef):Option[Syncable] = {
      target match {
        case map:Map[_,_] => 
          findLocalSyncable(map.asInstanceOf[JsonMap]) orElse 
            abort("can't find local instance for: %s", map.toString) 
        case _ => None
      }
    }
  }
  
  /** handle one edit operation in the protocol */
  private def processOneEdit(target:Syncable, edit:JsonMap) {
    log.trace("edit received: " + toJson(edit))

    edit match {
      case EditClear(clear:Boolean) => processClear(target)
      case EditPut(puts:List[_]) => processPut(target, puts.asInstanceOf[List[AnyRef]])
      case EditPut(putOne:Map[_,_]) => processPut(target, putOne :: Nil)
      case EditInsertAt(inserts:List[_]) => 
        inserts.asInstanceOf[List[(Syncable,Int)]] foreach tupled { 
          (elem:Syncable, at:Int) => 
            log.trace("insertAt, target: %s, elem: %s, at: %s", target, elem, at)
            processInsertAt(target, elem, at)
        }
      case EditRemoveAt(at:Int) => processRemoveAt(target, at)
      case EditMove(from:Int, to:Int) => processMove(target, from, to)
      case _ => err("unexpected #edit %s", edit.toString)
    }
  }
  
  
  private def processInsertAt(target:Syncable, elem:Syncable, at:Int) {
    target match {
      case seq:SyncableSeq[_] => seq.asInstanceOf[SyncableSeq[Syncable]].insert(at, elem)
      case _ => log.error("insertAt() only defined for SyncableSeq")
    }
  }
  
  private def processMove(target:Syncable, fromDex:Int, toDex:Int) {
    target match {
      case seq:SyncableSeq[_] => seq.asInstanceOf[SyncableSeq[Syncable]].move(fromDex, toDex)
      case _ => log.error("insertAt() only defined for SyncableSeq")
    }
  }
  
  private def processRemoveAt(target:Syncable, at:Int) {
    target match {
      case seq:SyncableSeq[_] => seq.asInstanceOf[SyncableSeq[Syncable]].remove(at)
      case _ => log.error("removeAt() only defined for SyncableSeq")
    }
  }
  
  /** handle the #edit.clear operation in the protocol */
  private def processClear(target:Syncable) {
    target match {
      case set:SyncableSet[_] => set.clear()
      case seq:SyncableSeq[_] => seq.clear()
      case _ => log.error("processClear against something other than a SyncableCollection: " + target)
    }  
  }
  
  def asSyncableSet(obj:AnyRef):Option[SyncableSet[Syncable]] = {
    obj match  {      
      case set:SyncableSet[_] => Some(set.asInstanceOf[SyncableSet[Syncable]])
      case _ => None
    }
  }
  def asJsonMap(obj:AnyRef):Option[JsonMap] = {
    obj match  {      
      case map:Map[_,_] => Some(map.asInstanceOf[JsonMap])
      case _ => None
    }
  }
  
  /** handle the #edit.put operation in the protocol */
  private def processPut(target:Syncable, puts:List[AnyRef]) {
    log.logLazy(TRACE, {"put " + puts.mkString + " into "+ target})

    for {
      set <- asSyncableSet(target) orElse
        err("processPut: expected target of a #edit.put to be a SyncableSet, but it's a: %s", target.toString)
      putElem <- puts 
      putMap <- asJsonMap(putElem) orElse 
        err("processPut: expected element in an #edit.put.  Expected a map, but it's a: %s", putElem.toString)        
      toAdd <- JsonTarget.unapply(putMap) orElse		// SCALA?
        err("processPut: unable to find this put instance: %s", putMap.toString) } {
      
      set += toAdd
    }
  }
  

  /* process a set of incoming jsync elements.  Received elements are  
   * @param json  collection of parsed json/jsync elements each */
  private def processSyncs(elems:List[JsonMap]) = {        
    elems foreach (json => {
      val objOpt = updateObj(json)	// update the scala object with json data	
      log.trace("processing sync: %s on %s", json, objOpt)
    })
  }

  /** Create or update a Syncable object from a json/jsync object or fragment.
   * If the object is known to the sync manager, it is updated to reflect the
   * received data, otherwise a new object is created.
   * 
   * @remote  json object or fragment to update
   * @return  the Syncable object found or created
   */
  private def updateObj(remote:JsonMap):Option[Syncable] = {
    localTarget(remote) map {local => 
      updateLocal(local, remote) 
      local
    }
  }
  
  /** find or create a local Syncable to match the received protocol object */
  private def localTarget(remote:JsonMap):Option[Syncable] = {
    remote match {
      case JsonSyncableId(ids) =>
        val kindOpt = matchOptString (remote get "$kind")
        kindOpt match { // sending '$kind' is our protocol clue to build a new object
          case Some(kind) =>
            Some(SyncManager.newSyncable(kind, ids))          
          case None =>
            App.app.get(ids)
        }    
      case _ => None
    }
  }
  
  /** find an existing syncable with the ids specified in the local map */
  private def findLocalSyncable(json:JsonMap):Option[Syncable] = {
    for {
      ids <- JsonSyncableId.unapply(json) 
      syncable <- App.app.get(ids)
    } yield {
        verifyKindsMatch(syncable, json)
        syncable 
    }  
  }
  
  /** update a local syncable object with fields from a received protocol object */
  private def updateLocal(local:Syncable, remote:JsonMap) {
    val setProperties = remote filter {
      case(name,value) => !SyncableInfo.isReserved(name)
    }
    val sanitized:Iterable[(String,Any)] = 
      for ((name, value) <- setProperties)
        yield (name, ReferencePatching.valueFromJson(local, name, value))

    local match {
      case json:SyncableJson => 
        log.error("client objects without a corresponding server class.  NYI")
        throw new NotYetImplemented
      case syncable:Syncable =>        
        verifyKindsMatch(local, remote)
        SyncManager.copyFields(sanitized, local)
    }
  }
  
  /** debug verification that the json kind matches the object */
  private def verifyKindsMatch(local:Syncable, remote:JsonMap) {
    for {remoteKind <- matchOptString (remote get "$kind")} {
      if (remoteKind != local.kind)
        log.error("local syncable kind (%s) does not match remote kind (%s)", local.kind, remoteKind)
    }
  }
      
}

/**
  * LATER, consider just forwarding changes to the partitions rather than fetching them.  
  * 
  * LATER, consider commit() ordering to reduce latency so that that changes to clients 
  *   go prior to changes to partitions.  
  * 
  */

