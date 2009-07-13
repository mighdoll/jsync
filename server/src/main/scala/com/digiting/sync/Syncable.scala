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

/**
 * All objects participating in the syncable system must extend Syncable.  Syncable
 * objects are reflectable/serializable by the SyncManager metaAccessors, and 
 * observable by the Observers system.
 * 
 * Syncable objects are uniquely identified by a (instanceId, partitionId) pair.
 * 
 * Each concrete Syncable subclass must define a 'kind'.  A kind is an implementation
 * neutral, protocol type designation.  Each kind is implemented by a unique class
 * on each syncable peer (scala server or javascript browser).  (Currently, the
 * kind strings are interpreted as javascript insantiation functions by the javascript
 * jsync library).
 * 
 * (LATER Syncable objects will also carry an instance versioning field to help the server
 * with conflict detection..)
 */
trait Syncable extends Observable {
  def kind:String	// 'kind' is what we call the syncable type or class     
  private val newIds = SyncManager.creating(this)	// ask the sync manager for an identity for this new object
  val id:String = newIds.instanceId	// unique id of this object within its partition
  val partition = newIds.partition	// partition this object call's home

  SyncManager.created(this)
  
  override def toString:String = {"[" + id + "," + partition.partitionId + "(" + prettyKind + ")]"}
  
  /** return a shallow serialization of this instance */
  def frozenCopy:JsonObject.JsonMap = {
    Message.toJsonMap(this)
  }
  
  def fullId = SyncableId(partition.partitionId, id)
        
  /** for pretty printing, get the the trailing part of com.foo.trailing */
  private def dotTail(str:String) = {
    val extractTail = ".*\\.(.*)".r
    
    if (str.contains(".")) {
      val extractTail(tail) = str
      tail
    } else {
      str
    }
  }
  
  private def prettyKind = dotTail(kind) 
  
  /* CONSIDER override def hashCode = id.hashCode  */    
}

import collection._
case class SyncableId(var partitionId:String, var instanceId:String) {
  def toJsonMap = immutable.Map("id" -> instanceId, "$partition" -> partitionId)
  def toJson = JsonUtil.toJson(toJsonMap)
}

/**
 * LATER make this a generic mechansim that any syncable can implement
 */
object SyncableInfo {
  /* true if the field isn't a user settable property (e.g. it's the sync id field) */
  def isReserved(fieldName:String):Boolean = {
    val newIds = ".*newIds".r
    fieldName match {
      case "kind" => true
      case "id" => true
      case "$partition" => true
      case "partition" => true
      case newIds() => true
      case _ => false
    }
  }
}
