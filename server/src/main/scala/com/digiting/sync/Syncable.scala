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
import net.lag.logging.Logger
import net.lag.logging.Level._
import SyncManager.withGetId
import SyncManager.NextVersion

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
  def kind:String		 // the public name for this type of Syncable, shared with javascript
  def kindVersion = "0"  // 'schema' version of this kind, to support migration of old versions
  private val newIds = SyncManager.creating(this)	// ask the sync manager for an identity for this new object
  val id:String = newIds.instanceId	// unique id of this object within its partition
  val partition = newIds.partition	// partition this object call's home
  var version = "initial"           // instance version of this object
  private val _log = Logger("Syncable")

  SyncManager.created(this)
  
  override def toString:String = {
    val partitionStr = 
      if (partition == null) 
        "partition?" 
      else 
        partition.partitionId
        
    if (_log.getLevel != null && _log.getLevel.intValue >= TRACE.intValue) {    
      JsonUtil.toJson(Message.toJsonMap(this), 0, true)      
    } else {
      String.format("{%s:%s}", shortKind, compositeId)
    }
  }
  
  def shortCompositeId:String = partition.partitionId.id.take(4) + "/" + id.take(5)
  def compositeId:String = fullId.toCompositeIdString
  
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
  
  private def shortKind = dotTail(kind) 
  
  private[sync] def newVersion():VersionChange = {
    val oldVersion = version
    version = 
      SyncManager.setNextVersion.take match {
        case Some(NextVersion(syncable, version)) =>
          assert(syncable == this)
          version
        case None => 
          RandomIds.randomUriString(10)
      }
    VersionChange(oldVersion, version)
  }
  
  /* CONSIDER override def hashCode = id.hashCode  */    
}


/** marker type indicating that this syncable is not transmitted to clients */
trait LocalOnly 

object CompositeId {
  val compositeIdRegex = """([a-zA-Z0-9_\.]+)/(.*)""".r
  def toSyncableId(compositeId:String):Option[SyncableId] = {
    compositeIdRegex.unapplySeq(compositeId) match {
      case Some(part :: id :: nil) => Some(SyncableId(PartitionId(part),id))
      case _ => None
    }
  }
}

import collection._
import CompositeId.compositeIdRegex
object SyncableId {
  def unapply(s:String):Option[SyncableId] = {
    compositeIdRegex.unapplySeq(s) match {
      case Some(part :: id :: nil) => Some(SyncableId(PartitionId(part),id))
      case _ => None
    }
  }
  def apply(partitionId:PartitionId, instanceIdString:String) = 
    new SyncableId(partitionId, InstanceId(instanceIdString))
  def apply(partitionId:PartitionId, instanceId:InstanceId) = 
    new SyncableId(partitionId, instanceId)
}

class SyncableId(val partitionId:PartitionId, val instanceId:InstanceId) {  
  def toJsonMap = immutable.Map("$id" -> instanceId.id, "$partition" -> partitionId.id)
  def toJson = JsonUtil.toJson(toJsonMap)
  def toCompositeIdString = partitionId.id + "/" + instanceId.id
  override def toString = toCompositeIdString
  def target = SyncManager.get(this)
}

object SyncableReference {
  def apply(partitionId:String, instanceId:String, kind:SyncManager.Kind) = {
    val id = SyncableId(PartitionId(partitionId), InstanceId(instanceId))
    new SyncableReference(id, kind)    
  }
  
  def apply(id:SyncableId) = {
    withGetId(id) {obj => 
      new SyncableReference(id, obj.kind)
    }
  }
  
  def apply(syncable:Syncable) = {
    new SyncableReference(syncable.fullId, syncable.kind)
  }
}

class SyncableReference(val id:SyncableId, val kind:SyncManager.Kind) extends 
  SyncableId(id.partitionId, id.instanceId) {
  override def toString = id.toCompositeIdString + "[" + kind + "]"
}

/**
 * LATER make this a generic mechansim that any syncable can implement
 */
object SyncableInfo {
  val log = Logger("SyncableInfo")
  /* true if the field isn't a user settable property (e.g. it's the sync id field) */
  def isReserved(fieldName:String):Boolean = {    
    val result = 
      fieldName match {
        case "kind" => true
        case "kindVersion" => true
        case "id" => true
        case "partition" => true
        case "newIds" => true
        case "version" => true
        case _ if fieldName contains "$" => true
        case _ if fieldName startsWith "_" => true
        case _ => false
      }
    log.trace("isReserved %s = %s", fieldName, result)

    result
  }
}
