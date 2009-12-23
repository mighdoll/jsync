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
import net.lag.logging.Logger

/** a pool of syncable objects.  
 * 
 * All objects in the pool are tracked for changes.  Interested parties can register to hear 
 * about changes via watchCommit().  Owners of the InstancePool call commit() to release
 * changes.
 */
class InstancePool(name:String) {
  val log = Logger("InstancePool")
  type CommitWatchFn = (Seq[ChangeDescription]) => Unit
  
  // holds all syncable objects we're caching in RAM, indexed by partition/id
  private val localObjects = new mutable.HashMap[String, Syncable]   // LATER this should be a WeakMap

  // changes made to any object in the pool
  private val changes = new ConcurrentLinkedQueue[ChangeDescription]	// SOON make this a concurrent linked queue

  // notifications to call on commit()
  private val commitWatchers = mutable.Set[CommitWatchFn]()
    
  /** find an object from the pool */
  def get(partition:String, id:String):Option[Syncable] = localObjects get key(partition, id)

  /** add a created change event for an object we've arleady added to the queue */
  def created(syncable:Syncable) = {
    log.ifTrace("created" + syncable)
    assert (localObjects contains key(syncable))
    changes add CreatedChange(syncable)
  }

  /** put an object into the pool */
  def put(syncable:Syncable) = {
    log.ifTrace("put" + syncable)
    localObjects put (key(syncable), syncable)
    Observers.watch(syncable, changeNoticed, this)
  }

  /** called when any object in the pool is change */
  private def changeNoticed(change:ChangeDescription) = {
    changes add change
  }

  /** remove an item from the cache */
  def remove(id:String, partitionName:String) = localObjects - key(partitionName, id)
  def remove(syncable:Syncable) = localObjects - key(syncable)
  def removeByCompositeId(compositeId:String) = localObjects - compositeId

  /** print entire pool for debugging porpoises */
  def printLocal {
    log.trace("local objects: ")
    for (local <- localObjects) {
      log.ifTrace("  " + local)
    }
  }
      
  /** commit current set of changes */
  def commit() = {
    val changesCopy = drainChanges()
    log.ifTrace("committing: " + {changesCopy map {_.toString} mkString("\n")})
    for (watcher <- commitWatchers) {
      watcher(changesCopy)
    }
  }

  private def drainChanges():Seq[ChangeDescription] = {
    val drained = new ListBuffer[ChangeDescription]()
    synchronized {
      var taken = changes.poll
      while (taken != null) {
        drained += taken
        taken = changes.poll
      }
    }
    drained
  }
    
  /** register for a callback on commit */
  def watchCommit(func:(Seq[ChangeDescription])=>Unit) {
    commitWatchers + func
  }

  /** index by key and partition id */    // TODO DRY with Syncable.compositeId 
  private def key(partition:String, id:String):String = partition + "/" + id
  /** index by key and partition id */
  private def key(syncable:Syncable):String = key(syncable.partition.partitionId, syncable.id)
}


/*
 * Application processing loop:
 *  -- incoming message is received by container/lift
 *  -- message is parsed and passed to Connection.Receiver (ActiveConnections maintains a map of current Connections)
 *  -- Receiver 
 * 		-- instantiates the local objects from the client, and from foreign and local partitions
 * 	    -- applies modifications from the message (change notification is queued)
 * 		-- sends notifications 
 * 		-- calls visibleSet.commit() 
 * 		-- sends modifications back to the partition
 * 	-- Connection, on commit() sends changes to the client via Connection.sender
 *  -- Partition 
*/ 
/*
 * Connection - info about the client connection including:  connectionId, defaultPartition.  
 * -- send and receive actors.
 * -- ClientSubscriptions
 *  
 * ClientSubscriptions
 * -- observe changes in visibleSet?  hmm.. seems like we'd really like to watch changes in the partition itself..
 * Receiver - handles incoming messages,
 * Cache
 * 
 
 */