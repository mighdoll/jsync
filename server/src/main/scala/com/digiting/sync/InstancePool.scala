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
import collection.mutable
import java.util.concurrent.ConcurrentLinkedQueue
import collection.mutable.ListBuffer
import com.digiting.util._
import Log2._

object WatchedPool extends UniqueId 
import WatchedPool.makeId

/** a pool of syncable objects.  
 * 
 * All objects in the pool are tracked for changes.  Interested parties can register to hear 
 * about changes via watchCommit().  Owners of the WatchedPool call commit() to release
 * changes.
 */
class WatchedPool(name:String)  {
  implicit private val log = logger("WatchedPool")
  private val debugId = makeId().toString
  
  trace2("#%s created", debugId) 
  // holds all syncable objects we're caching in RAM, indexed by partition/id
  private val localObjects = new mutable.HashMap[String, Syncable]   

  // changes made to any object in the pool
  private val changes = new ConcurrentLinkedQueue[ChangeDescription]	

  /** find an object from the pool */
  def get(partition:String, id:String):Option[Syncable] = localObjects get key(partition, id)

  /** add a created change event for an object we've already added to the queue */
  def created(syncable:Syncable) = {
    trace2("#%s created %s", debugId, syncable)
    assert (localObjects contains key(syncable))
    changes add CreatedChange(SyncableReference(syncable), Pickled(syncable),
      VersionChange(syncable.version, syncable.version))   
    // TODO CONSIDER generating the CreatedChange somewhere that makes more sense..
  }

  /** put an object into the pool */
  def put(syncable:Syncable) {
    trace2("#%s put: %s", debugId, syncable)   
    localObjects get (key(syncable)) map {found =>
      abort2("#%s put() but it's already in map: %s %s", debugId, found, syncable)
      
      localObjects put (key(syncable), syncable) // might be a revised instance..
    } orElse {
      localObjects put (key(syncable), syncable) // might be a revised instance..
      Observers.watch(syncable, this, changeNoticed)
      None
    } 
  }

  /** called when any object in the pool is change */
  def changeNoticed(change:ChangeDescription) = {
    trace2({"changeNoticed: " + change})
    changes add change
  }

  /** remove an item from the cache */
  def remove(id:String, partitionName:String) = localObjects - key(partitionName, id)
  def remove(syncable:Syncable) = localObjects - key(syncable)
  def removeByCompositeId(compositeId:String) = localObjects - compositeId

  /** print entire pool for debugging porpoises */
  def printLocal {
    info2("local objects: ")
    info2({localObjects mkString("  ", "  ", "")})
  }

  def drainChanges():Seq[ChangeDescription] = {
    val drained = new ListBuffer[ChangeDescription]()
    synchronized {
      var taken = changes.poll
      while (taken != null) {
        drained += taken
        taken = changes.poll
      }
    }
    trace2({drained map {"drainChanges: " + _.toString} mkString("\n")})
    drained
  }
    

  /** index by key and partition id */    // TODO DRY with Syncable.compositeId 
  private def key(partition:String, id:String):String = partition + "/" + id
  /** index by key and partition id */
  private def key(syncable:Syncable):String = key(syncable.id.partitionId.id, syncable.id.instanceId.id)
}


