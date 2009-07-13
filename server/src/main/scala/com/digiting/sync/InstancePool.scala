package com.digiting.sync
import collection.mutable

/** a pool of syncable objects.  
 * 
 * All objects in the pool are tracked for changes.  Interested parties can register to hear 
 * about changes via watchCommit().  Owners of the InstancePool call commit() to release
 * changes.
 */
class InstancePool(name:String) {
  type CommitWatchFn = (Seq[ChangeDescription]) => Unit
  
  // holds all syncable objects we're caching in RAM, indexed by id
  private val localObjects = new mutable.HashMap[String, Syncable]   // LATER this should be a WeakMap

  // changes made to any object in the pool
  private val changes = new mutable.Queue[ChangeDescription]	// SOON make this a concurrent linked queue

  // notifications to call on commit()
  private val commitWatchers = mutable.Set[CommitWatchFn]()
    
  /** find an object from the pool */
  def get(partition:String, id:String):Option[Syncable] = localObjects get key(partition, id)

  /** add a created change event for an object we've arleady added to the queue */
  def created(syncable:Syncable) = {
    assert (localObjects contains key(syncable))
    changes += CreatedChange(syncable)
  }

  /** put an object into the pool */
  def put(syncable:Syncable) = {
//    Console println("InstancePool(" + name + ").put: " + syncable)
    localObjects put (key(syncable), syncable)
    Observers.watch(syncable, changeNoticed, this)
  }

  /** called when any object in the pool is change */
  private def changeNoticed(change:ChangeDescription) = {
    changes += change
  }

  /** remove an item from the cache */
  def remove(id:String) = localObjects - id

  /** print entire pool for debugging porpoises */
  def printLocal {
    Console println("local objects: ")
    for (local <- localObjects) {
      Console println("  " + local)
    }
  }
      
  /** commit current set of changes */
  def commit() = {
    for (watcher <- commitWatchers) {
      watcher(changes)
    }
    changes.clear
  }
    
  /** register for a callback on commit */
  def watchCommit(func:(Seq[ChangeDescription])=>Unit) {
    commitWatchers + func
  }

  /** index by key and partition id */
  private def key(partition:String, id:String):String = partition + "-" + id
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