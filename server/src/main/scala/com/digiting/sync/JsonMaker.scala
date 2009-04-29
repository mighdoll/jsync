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

import _root_.net.liftweb.util._
import com.digiting.sync.syncable._
import com.digiting.sync.aspects.Observable
import JsonUtil._
import collection._
import Observation._
import JsonMessageControl._
import JsonObject._

/* Construct json-sync messages */
object MakeMessage {
  
  /* create a protocol message from set of observed changes to syncable objects
   * and syncable collections */
  def constructTransaction(pendingChanges:Seq[ChangeDescription]):JsonMessage = {
    def membershipChange(change:MembershipChange){
      
    }
    // TODO coalesce changes on the same object 
    // TODO need to watch deep
    // TODO move response to separate actor   
    val edits = new mutable.ListBuffer[JsonMap]
    val syncs = new mutable.ListBuffer[JsonMap]
    
    for (change <- pendingChanges) {
      change match {
        case propChange:PropertyChange => 
          val target = propChange.target.asInstanceOf[Syncable]
          val value = toJsonMapValue(propChange.newValue)
          val props = immutable.Map("id" -> target.id, propChange.property -> value)
          syncs + props
        case watchChange:WatchChange => 
          // (we don't currently send the client the subscription root)
          val newObj = watchChange.newValue.asInstanceOf[Syncable]
          syncs + toJsonMap(newObj)
        case putChange:PutChange => {
          val targetId = putChange.operationTarget.asInstanceOf[Syncable].id
          
            // LATER handle a possible array of values as new Value
          val editItems = toJsonMapValue(putChange.newValue)          
          val edit = ImmutableJsonMap("#edit" -> targetId,
                                      putChange.operation -> editItems)
          edits + edit          
        }
        case change => Log.warn("unhandled change: " + change)
      }
    }
    
    new JsonMessage(edits.toList, syncs.toList)
  }

  
  /* convert a syncable object to a map of name,value properties.
   * In the returned map, the property names are strings.  The values
   * are strings, doubles, or JsonRefs */
  def toJsonMap(obj:Syncable):JsonMap = {
    val props = new MutableJsonMap
    props + ("id" -> obj.id)
    props + ("kind" -> obj.kind)
    for ((name, value) <- SyncableAccessor.properties(obj)) {
      props + (name -> toJsonMapValue(value))
    } 
    props
  }
  
  def toJsonMapValue(value:Any):Any = {
    value match {
      case syncable:Syncable => new JsonRef(syncable.id)
      case array:List[_] => throw new NotYetImplemented
      case any => any                                      
    }                                        
  }
    
  /** format a syncable object as a json object */
  def toJson(obj:Syncable):String = {      
    val map = toJsonMap(obj)
    JsonUtil.toJson(map)
  }
}
