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
import SyncManager.NextVersion
import com.digiting.util._
import Log2._

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
  val id = SyncManager.creating(this) // ask the sync manager for an identity for this new object
  
  def kind:String		 // the public name for this type of Syncable, shared with javascript
  def kindVersion = "0"  // 'schema' version of this kind, (used to support data migration)
  def partition = Partitions.getMust(id.partitionId.id)	// partition this object calls home
  var version = "initial"           // instance version of this object
  implicit private lazy val _log = logger("Syncable")
  def fullId = id   // TODO get rid of this

  SyncManager.created(this)
  
  override def toString:String = {
    String.format("{%s:%s}", shortKind, compositeId)
  }
  
  def shortCompositeId:String = id.partitionId.id.take(4) + "/" + id.instanceId.id.take(5)
  def compositeId:String = fullId.toCompositeIdString
  
  /** return a shallow serialization of this instance */
  def frozenCopy:JsonObject.JsonMap = {
    Message.toJsonMap(this)
  }
  
        
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
    val newVersion = 
      SyncManager.setNextVersion.take match {
        case Some(NextVersion(syncable, version)) =>
          assert(syncable == this)
          version
        case None => 
          "v" + RandomIds.randomId(10)
      }
    VersionChange(oldVersion, newVersion)
  }
  
  /* CONSIDER override def hashCode = id.hashCode  */    
}


/** marker type indicating that this syncable is not transmitted to clients */
trait LocalOnly 

/**
 * LATER clean this up
 * LATER make this a generic mechansim that any syncable can implement
 */
object SyncableInfo {
  implicit private lazy val log = logger("SyncableInfo")
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
    trace2("isReserved %s = %s", fieldName, result)

    result
  }
}
