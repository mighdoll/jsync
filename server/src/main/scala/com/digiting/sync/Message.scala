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

import JsonObject._
import collection._
import com.digiting.sync.syncable._
import com.digiting.sync.aspects.Observable
import JsonUtil._
import JsonObject._
import net.lag.logging.Logger
import com.digiting.util.LogHelper
import SyncManager.withGetId


/** A JsonSync protocol message in a form that's easy to convert to/from json strings */
class Message(var xactNumber: Int, val controls:List[JsonMap], val edits:List[JsonMap], 
	val syncs:List[JsonMap]) {
  
  def this(edits:List[JsonMap], syncs:List[JsonMap]) = this(-1, Nil, edits, syncs)
  def this(controls:List[JsonMap]) = this(-1, controls, Nil, Nil)
  
  /** convert a message to a json object string */
  def toJson:String = {
    val metas = new mutable.ListBuffer[JsonMap]
    metas ++ controls    
    metas append ImmutableJsonMap("#transaction" -> xactNumber)

    val jsons = new mutable.ListBuffer[String]
    appendJsonMaps(jsons, metas.toList, syncs, edits)
    jsons.mkString("[", ",\n", "]\n")
  }
  
  /** search for a json property among the control json objects */
  def findControlProperty(controlName:String):Option[Any] = {
    var found:Option[Any] = None
    
    for (control <- controls;
      (name,value) <- control) {
      if (controlName == name) {
        found = Some(value)
      }
    }
    found
  }
  
  def isEmpty:Boolean = {
    edits.isEmpty && syncs.isEmpty && xactNumber == -1 && 
      (controls.isEmpty || (controls.length ==1 && (controls.first contains("#token"))))
  }
  
  override def toString = 
    "Message: " + xactNumber + "  syncs: " + syncs.size + "  edits: " + edits.size 
  
  private def appendJsonMaps(jsons:mutable.ListBuffer[String], maps:List[JsonMap]*) {
    for (map <- maps;
         elem <- map) {
//      var json = JsonUtil.toJson(elem, 2)
//      Log.info("appendJsonMaps() elem toJson: " + json)
      jsons append JsonUtil.toJson(elem, 2, false)
    }
  }  
}

/**
 * Utility functions for creating Messages and Message components 
 */
object Message extends LogHelper {
  val log = Logger("Message")
  /** create a protocol message from set of observed changes to syncable objects
   * and syncable collections */
  def makeMessage(pendingChanges:Seq[ChangeDescription]):Message = {    
    val edits = new mutable.ListBuffer[JsonMap]		// changes to collections (e.g. put in set)
    val syncs = new mutable.ListBuffer[JsonMap]		// create/update changes to objects
    
    for (change <- pendingChanges) {
      log.trace("make message from change: " + change)
      change match {
        case propChange:PropertyChange => 
          syncs + propertyChange(propChange)
        case watch:BeginWatch=> 
          withGetId(watch.newValue) { target =>
            syncs + toJsonMap(target)
          }
        case base:BaseMembership =>
          base.target.target match {
            case Some(set:SyncableSet[_]) =>
              edits ++ putContents(base)
            case Some(seq:SyncableSeq[_]) =>
              edits ++ insertAtContents(base)              
            case _ =>
              err("unexpected target of baseMembership: " + base.target)              
          }
        case put:PutChange =>           
          edits + memberChangeToEdit(put, put.newValue)
        case remove:RemoveChange =>
          edits + memberChangeToEdit(remove, remove.oldValue)
        case removeAt:RemoveAtChange =>
          edits + removeAtChange(removeAt)
        case insertAt:InsertAtChange =>
          edits + insertAtChange(insertAt)
        case unwatch:EndWatch=> // do nothing (client garbage collects on its own)
        case clear:ClearChange =>
          edits + clearChange(clear) 
        case move:MoveChange =>
          edits + moveChange(move)
        case change => log.error("Message.makeMessage() unhandled change: " + change)
      }
    }
     
    // LATER coalesce multiple changes to the same object into one operation    
    new Message(edits.toList, syncs.toList)
  }
  
  private def propertyChange(propChange:PropertyChange):JsonMap = {              
    val value = toJsonMapValue(propChange.newValue.value)
    immutable.Map(propChange.property -> value) ++ propChange.target.toJsonMap
  }
  
  private def clearChange(change:ClearChange):JsonMap = {
    ImmutableJsonMap(editTarget(change), change.operation -> "true")
  }
  
  private def moveChange(change:MoveChange):JsonMap = {
    val moveDex = ImmutableJsonMap("from" -> change.fromDex, "to" -> change.toDex)
    ImmutableJsonMap(editTarget(change), change.operation -> moveDex)	  
  }
  
  private def removeAtChange(change:RemoveAtChange):JsonMap = {
    ImmutableJsonMap(editTarget(change), change.operation -> change.at) 
  }
  
  private def insertAtChange(change:InsertAtChange):JsonMap = {
    insertAtToJsonMap(change.target, change.newValue, change.at)
  }
  
  def insertAtToJsonMap(target:SyncableId, elem:SyncableId, at:Int):JsonMap = {
    val ats = ImmutableJsonMap("at" -> at, 
      "elem" -> elem.toJsonMap) :: Nil
    
    ImmutableJsonMap(editTargetJsonPair(target), 
      "insertAt" -> ats)   
  }

  private def editTargetJsonPair(targetId:SyncableId) = {
    ("#edit" -> targetId.toJsonMap)
  }
  
  private def editTarget(change:ChangeDescription) = {
    editTargetJsonPair(change.target)
  }
  
  private def insertAtContents(base:BaseMembership):Iterable[JsonMap] = {
    insertAts(base.target, base.members)
  }
  
  def insertAts(target:SyncableId, elems:Iterable[SyncableId]):List[JsonMap] = {
    val inserts = 
      for ((elem, index) <- elems.toList.zipWithIndex) 
        yield ImmutableJsonMap("at" -> index, "elem" -> elem.toJsonMap) 
    
    log.trace("insertAt: %s", inserts.size)
    val edit = ImmutableJsonMap(
      "#edit" -> target.toJsonMap,
      "insertAt" -> inserts) :: Nil
    
    edit
  }
  
  private def memberChangeToEdit(change:MembershipChange, operandElements:Any):JsonMap = {
    log.info("memberChangeToEdit %s %s", change, operandElements)
    val editItems = operandElements match {
      case single:SyncableId => single.toJsonMap :: Nil
      case list:List[_] => 
        for (elem <- list) 
          yield elem.asInstanceOf[SyncableId].toJsonMap          
      case _ => err("memberChangeToEdit() unexpected change values " + change) 
    }
    ImmutableJsonMap(editTarget(change), change.operation -> editItems)
  }
  
  private def putContents(base:BaseMembership):List[JsonMap] = {
    putToJsonMap(base.target, base.members)
  }
  
  def putToJsonMap(target:SyncableId, puts:Seq[SyncableId]):List[JsonMap] = {
    if (!puts.isEmpty) {
      ImmutableJsonMap(
        "#edit" -> target.toJsonMap,
        "put" -> puts) :: Nil
    } else {
      Nil
    }
  }

  /* convert a syncable object to a map of name,value properties.
   * In the returned map, the property names are strings.  The values
   * are strings, doubles, or JsonRefs */
  def toJsonMap(obj:Syncable):JsonMap = {
    val props = new MutableJsonMap
    props + ("$id" -> obj.id)
    props + ("$kind" -> obj.kind)
    props + ("$partition" -> obj.partition.partitionId)
    for ((name, value) <- SyncableAccessor.properties(obj)) {
      props + (name -> toJsonMapValue(value))
    } 
    props
  }

  /** convert objects intended for json values into a json std forms
   * (JsonUtil.toJson will later convert them to json text) */
  private def toJsonMapValue(value:Any):Any = {
    value match {
      case syncable:Syncable => new JsonRef(syncable.fullId)  // TODO get rid of this?
      case id:SyncableId => new JsonRef(id)
      case map:Map[_,_] => map
      case seq:Seq[_] => seq.toList
      case any => any                                      
    }                                        
  }
    
  /** format a syncable object as a json object */
  def toJson(obj:Syncable):String = {      
    val map = toJsonMap(obj)
    JsonUtil.toJson(map)
  }
}

case class JsonRef(val fullId:SyncableId) { 
  override def toString =
    "$ref:" + fullId
  def toJson = "{\"$ref\":" + fullId.toJson + "}"
}
