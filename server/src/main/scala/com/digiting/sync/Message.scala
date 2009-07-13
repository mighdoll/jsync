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
import _root_.net.liftweb.util._
import com.digiting.sync.syncable._
import com.digiting.sync.aspects.Observable
import JsonUtil._
import JsonObject._


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
    edits.isEmpty && syncs.isEmpty && 
      (controls.isEmpty || (controls.length ==1 && (controls.first contains("#token"))))
  }
  
  override def toString = 
    "Message: " + xactNumber + "  syncs: " + syncs.size + "  edits: " + edits.size 
  
  private def appendJsonMaps(jsons:mutable.ListBuffer[String], maps:List[JsonMap]*) {
    for (map <- maps;
         elem <- map) {
      jsons append JsonUtil.toJson(elem, 2)
    }
  }
  
}

/**
 * Utility functions for creating Messages and Message components 
 */
object Message {
  /** create a protocol message from set of observed changes to syncable objects
   * and syncable collections */
  def makeMessage(pendingChanges:Seq[ChangeDescription]):Message = {    
    val edits = new mutable.ListBuffer[JsonMap]		// changes to collections (e.g. put in set)
    val syncs = new mutable.ListBuffer[JsonMap]		// create/update changes to objects
    
    for (change <- pendingChanges) {
//      Console println ("make message from change " + change)
      change match {
        case propChange:PropertyChange => 
          val target = propChange.target.asInstanceOf[Syncable]
          val value = toJsonMapValue(propChange.newValue)
          val props = immutable.Map(propChange.property -> value) ++ target.fullId.toJsonMap
          syncs + props
        case watch:WatchChange => 
          val newObj = watch.newValue.asInstanceOf[Syncable]
          syncs + toJsonMap(newObj)
          newObj match {
            case collection:SyncableCollection =>
              edits ++ putCollectionContents(collection)
            case _ =>
          }
        case put:PutChange =>           
          edits + memberChangeToEdit(put, put.newValue)
        case remove:RemoveChange =>
          edits + memberChangeToEdit(remove, remove.oldValue)
        case unwatch:UnwatchChange => // do nothing (client garbage collects on its own)
        case change => Log.error("makeMessage() unhandled change: " + change)
      }
    }
     
    // LATER coalesce multiple changes to the same object into one operation    
    new Message(edits.toList, syncs.toList)
  }
  
  private def memberChangeToEdit(change:MembershipChange, operandElements:Any):JsonMap = {
	val target = change.target.asInstanceOf[Syncable]   
	val editItems = operandElements match {
	 case single:Syncable => single.fullId.toJsonMap :: Nil
	 case list:List[_] => for (elem <- list) 
       yield elem.asInstanceOf[Syncable].fullId.toJsonMap
	      
	  case _ => Log.error("memberChangeToEdit() unexpected change values " + change) 
	}
    val edit = ImmutableJsonMap("#edit" -> target.fullId.toJsonMap,	
                                change.operation -> editItems)
    edit
  }
  
  private def putCollectionContents(collection:SyncableCollection):List[JsonMap] = {
    val edits = new mutable.ListBuffer[JsonMap]
    val ids = for (elem <- collection.syncableElements) yield elem.fullId.toJsonMap
    if (!ids.isEmpty) {
	  val edit = ImmutableJsonMap("#edit" -> collection.fullId.toJsonMap,
	                              "put" -> ids)
	  edits + edit
      edits.toList
    } else {
      Nil
    }
  }

  
  /* convert a syncable object to a map of name,value properties.
   * In the returned map, the property names are strings.  The values
   * are strings, doubles, or JsonRefs */
  def toJsonMap(obj:Syncable):JsonMap = {
    val props = new MutableJsonMap
    props + ("id" -> obj.id)
    props + ("kind" -> obj.kind)
    props + ("$partition" -> obj.partition.partitionId)
    for ((name, value) <- SyncableAccessor.properties(obj)) {
      props + (name -> toJsonMapValue(value))
    } 
    props
  }

  /** convert objects intended for json values into a generic form.
   * (JsonUtil.toJson will later convert them to json text) */
  private def toJsonMapValue(value:Any):Any = {
    value match {
      case syncable:Syncable => new JsonRef(syncable.fullId)
      case array:List[_] => array
      case any => any                                      
    }                                        
  }
    
  /** format a syncable object as a json object */
  private def toJson(obj:Syncable):String = {      
    val map = toJsonMap(obj)
    JsonUtil.toJson(map)
  }
}

case class JsonRef(val fullId:SyncableId) { 
  override def toString =
    "$ref:" + fullId
  def toJson = "{\"$ref\":" + fullId.toJson + "}"
}
