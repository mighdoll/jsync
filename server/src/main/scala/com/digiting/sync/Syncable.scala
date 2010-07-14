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
 * Base trait for objects participating in the syncable system.  Syncable objects can
 * be tracked for changes, persisted to storage partitions, and/or sent over the wire to
 * other nodes.
 * 
 * Syncable objects are uniquely identified by a (instanceId, partitionId) pair.
 * 
 * Each concrete Syncable subclass must have a 'kind', a slightly more javascript 
 * friendly name for the type.
 * 
 * When a Syncable instance is saved to persistent storage, it will be tagged by 
 * kind and id, and all the public properties of the syncable will be saved.  Ditto for 
 * sending over the wire to javascript implementations.  In other words, Syncable subclasses
 * act as their own IDL (interface description language).
 */
trait Syncable extends Observable {
  implicit private lazy val _log = logger("Syncable")
  val id = SyncManager.creating(this) // ask the sync manager for an identity for this new object
  var version = "initial"             // instance version of this object, changes every time the object is updated
  
  def kind:String		     // the public name for this type of Syncable, shared with javascript
  def kindVersion = "0"  // 'schema' version of this kind, (so we can migrate old versions of persisted Syncables)
  def partition = Partitions.getMust(id.partitionId.id)	// partition this object calls home

  SyncManager.created(this)
  
  override def toString:String = {
    String.format("{%s:%s}", shortKind, id)
  }
 
  /** return a shallow serialization of this instance */
  def frozenCopy:JsonObject.JsonMap = {
    Message.toJsonMap(this)
  }
  

  /** for pretty printing, get the the trailing part of com.foo.trailing */
  private def shortKind = dotTail(kind)
  
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
