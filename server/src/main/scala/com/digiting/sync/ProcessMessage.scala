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
import collection._
import com.digiting.sync.syncable._
import JsonUtil._
import com.digiting.util.HandyLog
import com.digiting.util.LogHelper
import com.digiting.sync.TryCast.toSomeString

/** this the core routine that reacts to a transaction of object changes from a remote client. 
 * 1) Fetch objects in the transaction from partitions so that we processcan operate on them 
 *    (LATER, consider just forwarding changes to the partitions rather than fetching them)
 * 2) make client mutations as per the client's message 
 * 3) notify server applications of the changes (apps may make further mutations), 
 * 4) commit changes to storage and to listening clients 
 * 5) AppContext watches commit() too, and queues change messages back to the client
 */
class ProcessMessage(app:AppContext) extends HandyLog {
  val logging = "ProcessMessage"
  
  /** process one incoming client transaction */
  def process(message:Message) = {    
	log.logLazy(TRACE, {"processMessage:" + message.toJson})
    app.withApp {
      try {
        // apply modifications in the transaction to all objects as a group, before notifying app
        Observers.pauseNotification {
          Observers.currentMutator.withValue(app.connection.connectionId) {
	  	      processSyncs(message.syncs)
	  	      processEdits(message.edits)
//          app.commit()	// commit client changes  (TODO need client long poll here, because this will produce two transactions for the client)
          
	  	    // release notifications to DeepWatch early so that WatchChanges have the client connection as the mutator
            Observers.releasePaused(_.isInstanceOf[DeepWatch])
          }          
        }          
        ; // notifications of changes are released.  Responses are processed in app context 
      } catch {
        // LATER send client a protocol reset message, reset our state too
        case e:Exception => log.error(e, "exception procesing request")
      } 
    }
  } 

    
  /** process changes to syncable collections */
  private def processEdits(elems:List[Map[String,Any]]):Unit = {
    for (edit <- elems) {
      val editOpt = edit get "#edit"
      editOpt match {
        case Some(JsonTarget(target)) => processOneEdit(target, edit) 
        case _ => log.error("processEdits can't find target of #edit: %s", edit)
      }
    }
  }
  
  /** extract the local syncable referenced by a an id, partitionId pair in a jsonMap */
  object JsonTarget {
    def unapply(target:AnyRef):Option[Syncable] = {
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
      case EditPut(puts:List[_]) => processPut(target, puts.asInstanceOf[List[AnyRef]])
      case EditPut(putOne:Map[_,_]) => processPut(target, putOne :: Nil)
      case EditClear(clear:Boolean) => processClear(target)
      case EditInsertAt(elem:Syncable, at:Int) => processInsertAt(target, elem, at)
      case EditRemoveAt(at:Int) => processRemoveAt(target, at)
      case EditMove(from:Int, to:Int) => processMove(target, from, to)
      case _ => log.error("Reciever: unexpected #edit %s", edit)
    }
  }
  
  object EditRemoveAt {
    def unapply(edit:Map[String,Any]):Option[Int] = {
      for {remove <- edit get "removeAt"
    	   at <- doubleToIntOpt(remove)} 
        yield at     
    }
  }
  
  object EditMove {
    def unapply(edit:Map[String,Any]):Option[(Int, Int)] = {
      for {moveParam <- edit get "move"
           moveObj <- toJsonMap(moveParam)
           fromDexDouble <- moveObj get "from"
           fromDex <- doubleToIntOpt(fromDexDouble)
           toDexDouble <- moveObj get "to"
           toDex <- doubleToIntOpt(toDexDouble)
      } 
      yield (fromDex, toDex) 	   
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
        case Some(_) => 
          log.error("Unexpected clear value in map: %s", edit)
          None
        case _ => 
          None
      }
    }
  }
  
  object EditInsertAt {
    def unapply(edit:Map[String,Any]):Option[(Syncable,Int)] = {
      for (insert <- edit get "insertAt";
           insertMap <- toJsonMap(insert);
           at <- insertMap get "at";
           atInt <- doubleToIntOpt(at);
      	   elem <- insertMap get "elem";
           elemMap <- toJsonMap(elem);
           ids <- JsonSyncableIdentity.unapply(elemMap);
      	   syncable <- SyncManager.get(ids))
      yield ((syncable, atInt))      
    }
  }
  
  private def doubleToIntOpt(possibleDouble:Any):Option[Int] = {
    possibleDouble match {
      case d:Double => Some(d.intValue)
      case _ => None
    }
  }
  
  private def parseIntOpt(parse:Any):Option[Int] = {
    parse match {
      case s:String => 
        try {
          Some(s.toInt)
        } catch {
          case e: NumberFormatException => None 
        }
      case _ => None
    }    
  }
  
  private def toStringOpt(possibleString:Any):Option[String] = {
    possibleString match {
      case s:String => Some(s)
      case _ => None
    }
  }
  
  private def toJsonMap(toMap:Any):Option[Map[String,Any]] = {
    toMap match {
      case map:Map[_,_] => Some(map.asInstanceOf[Map[String,Any]])
      case _ => None
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
  def asJsonMap(obj:AnyRef):Option[Map[String,Any]] = {
    obj match  {      
      case map:Map[_,_] => Some(map.asInstanceOf[Map[String,Any]])
      case _ => None
    }
  }
  
  /** handle the #edit.put operation in the protocol */
  private def processPut(target:Syncable, puts:List[AnyRef]) {
    log.logLazy(TRACE, {"put " + puts.mkString + " into "+ target})

    for {
      set <- asSyncableSet(target) orElse
        error("processPut: expected target of a #edit.put to be a SyncableSet, but it's a: %s", target.toString)
      putElem <- puts 
      putMap <- asJsonMap(putElem) orElse 
        error("processPut: expected element in an #edit.put.  Expected a map, but it's a: %s", putElem.toString)        
      toAdd <- JsonTarget.unapply(putMap) orElse		// SCALA?
        error("processPut: unable to find this put instance: %s", putMap.toString) } {
      
      set += toAdd
    }
  }
  

  /* process a set of incoming jsync elements.  Received elements are  
   * @param json  collection of parsed json/jsync elements each */
  private def processSyncs(elems:List[Map[String,Any]]) = {        
    elems foreach (json => {
      val objOpt:Option[Syncable] = updateObj(json)	// update the scala object with json data	
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
  private def updateObj(remote:Map[String, Any]):Option[Syncable] = {
    localTarget(remote) map {local => 
      updateLocal(local, remote) 
      local
    }
  }
  
  /** find or create a local Syncable to match the received protocol object */
  private def localTarget(remote:Map[String, Any]):Option[Syncable] = {
    remote match {
      case JsonSyncableIdentity(ids) =>
        val kindOpt = toSomeString (remote get "kind")
        kindOpt match { // sending '$kind' is our protocol clue to build a new object
          case Some(kind) =>
            SyncManager.newSyncable(kind, ids)          
          case None =>
            SyncManager.get(ids)
        }    
      case _ => None
    }
  }
  
  /** find an existing syncable with the ids specified in the local map */
  private def findLocalSyncable(json:Map[String, Any]):Option[Syncable] = {
    for {
      ids <- JsonSyncableIdentity.unapply(json) 
      syncable <- SyncManager.get(ids)
    } yield {
        verifyKindsMatch(syncable, json)
        syncable 
    }  
  }
  
   
  /** extract a reference to another syncable from the parsed json results.
    * object references are encoded as $ref objects */
  object Reference extends LogHelper {
    val log = Logger("Reference")
    def unapply(value:Any):Option[Syncable] = {
      for {jsonObj <- toJsonMap(value)
           refVal <- jsonObj get "$ref"
           refObj <- toJsonMap(refVal) 
      	   ids <- JsonSyncableIdentity.unapply(refObj) orElse
             error("can't parse syncable id from: ", refObj.toString)
           syncable <- SyncManager.get(ids) orElse
             error("syncable not retrieved for: %s", ids.toString)
      } yield syncable
     }
  }
  
  private def valueFromJson(value:Any):AnyRef = {
    value match {
      case Reference(target) => target
      case PrimitiveJsonValue(primitiveObj) => primitiveObj
      case _ => 
        log.error("received unexpected value type in JSON: " + value); null
        null
    }
  }
  
  /** update a local object with fields from a received protocol object */
  private def updateLocal(local:Syncable, remote:Map[String, Any]) {
    val setProperties = remote filter {
      case(name,value) => !SyncableInfo.isReserved(name)
    }
    val sanitized:Iterable[(String,AnyRef)] = for ((name,value) <- setProperties)
      yield (name, valueFromJson(value))

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
  private def verifyKindsMatch(local:Syncable, remote:Map[String,Any]) {
    for (remoteKind <- toSomeString (remote get "kind")) {
      if (remoteKind != local.kind)
        log.error("local syncable kind (%s) does not match remote kind (%s)", local.kind, remoteKind)
    }
  }
      

  object JsonSyncableIdentity extends LogHelper {
    val log = Logger("JsonSyncableIdentity")
    /** get the instance and partition ids out of map.  If the partitionId isn't
      * specified, use the connections default partition.  */
    def unapply(json:Map[String, Any]):Option[SyncableIdentity] = {
      val foundIds:Option[SyncableIdentity] = 
      toSomeString (json get "id") match { 
        case Some(instanceId) => {
          toSomeString (json get "$partition") match {
            case Some(partitionId) if (partitionId == ".transient") => 
              Some(SyncableIdentity(instanceId, app.transientPartition))
            case Some(partitionId) if (partitionId == ".implicit") => 
              Some(SyncableIdentity(instanceId, app.implicitPartition))
            case Some(partitionId) =>               
              Partitions get partitionId match {
                case Some(partition) => Some(SyncableIdentity(instanceId, partition))
                case None => 
                  error("partition %s not found", partitionId)
              } 
            case None => 
              Some(SyncableIdentity(instanceId, app.defaultPartition))
          }         
        } 
        case None => error("id not found: %s", json.toString)
      }
        
      log.info("found: %s", foundIds.toString)
      foundIds orElse {
        error("unable to parse ids in map: %s", json.toString)
      }
    }
  }
}


