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

import scala.util.DynamicVariable
import com.digiting.util._
import Partition._

/** A storage segment of syncable objects 
 * 
 * Put() and update() operations are asynchronous and eventually consistent.  
 * so get() might not return the most recently put() data (e.g. when servers fail and recover).
 * 
 * The current implementation does not guarantee durability.  If the server crashes right after
 * put() is called, the data is lost.
 */
abstract class Partition(val partitionId:String) extends RamWatches 
    with ConcretePartition with LogHelper {  
  protected lazy val log = logger("Partition")
  val id = PartitionId(partitionId)
  private val currentTransaction = new DynamicVariable[Option[Transaction]](None)
  private[sync] val published = new PublishedRoots(this)
  
  /** all get,modify,watch operations should be called within a transaction */
  def withTransaction[T](fn: =>T):T = {
    val tx = new Transaction
    currentTransaction.withValue(Some(tx)) {
      val result = fn
      commit(tx) && {notifyChanges(tx); true}
      result
    }
  }

  /** Fetch an object or a collection.  
   * (Creates an implicit transaction if none is currently active) */
  def get(instanceId:InstanceId):Option[Syncable] = {
    val syncable = 
      autoTransaction { tx =>
        for {
          pickled <- get(instanceId, tx)
          syncable:Syncable = pickled.unpickle // loads referenced objects too
        } yield syncable
      }
    log.trace("#%s get(%s) = %s", partitionId, instanceId, syncable)
    syncable
  }
  
    /** create, update, delete or observer an object or a collection */
  def modify(change:StorableChange) { 
    inTransaction {tx => 
      trace("#%s update(%s)", partitionId, change)
      modify(change, tx)
      tx.changes += change
    }    
  }

  
  /** 
   * Observe an object for changes.
   * 
   * After the specified duration in milliseconds, the watch is discarded.
   */
  def watch(id:InstanceId, pickledWatch:PickledWatch) {
    inTransaction {tx =>
      watch(id, pickledWatch, tx)
    }
  }
  
  /** unregister a previously registered watch */
  def unwatch(id:InstanceId, pickledWatch:PickledWatch) {
    inTransaction {tx =>
      unwatch(id, pickledWatch, tx)
    }    
  }
  
  /** name to object mapping of for well known objects in the partition.  The published
   * roots are persistent, so clients of the partition can use well known names to get
   * started with the partition data, w/o having to resort to preserving object ids, 
   * or querying, etc.
   * 
   * Eventually, objects in the partition that are not referenced directly or 
   * indirectly by one of these roots may be garbage collected by the partition.  
   * (garbage collection is NYI)
   */  
  def publish[T<:Syncable](publicName:String, root:T):T = {
    published.create(publicName, root)
    root
  }
  
  /** map a name to a function that produces an object for that name dynamically. */  
  def publish(publicName:String, generator: ()=>Option[Syncable]) {
    published.createGenerated(publicName, generator)
  }
    
  /** destroy this partition and its contents */
  def deletePartition() {
    Partitions.remove(id.id)
    deleteContents()
  }
  
  /** verify that we're currently in a valid transaction*/
  private[this] def inTransaction[T](fn: (Transaction)=>T):T =  {
    currentTransaction value match {
      case Some(tx:Transaction) => 
        fn(tx)
      case None => 
        throw new InvalidTransaction("no current transaction")
    }
  }
    
  /** create a single operation transaction if necessary */
  private def autoTransaction[T](fn:(Transaction) =>T):T = {
    currentTransaction value match {
      case Some(tx) => 
        fn(tx)
      case None =>
        withTransaction {
          inTransaction(fn)
        }
    }
  }
  
    
  /** call the watching client */
//  protected[sync] def notify(watch:PickledWatch, change:DataChange, tx:Transaction) {}
  private object WatchableChange {
    def apply(change:StorableChange):Option[DataChange] = {
      change match {
        case c:CreatedChange => None
        case dataChange:DataChange => Some(dataChange)
        case obs:ObservingChange => None
      }
    }
  }

  
    /** notify watchers of changes */
  private def notifyChanges(tx:Transaction) {    
    if (hasStorage) {
      // collect watchers for each change
      val watched:Seq[(DataChange,Set[PickledWatch])] = 
        for {
          change <- tx.changes
          watchable <- WatchableChange(change)           
          targetId = change.target.instanceId
          watches = getWatches(targetId, tx) 
          invalid = watches filter(System.currentTimeMillis > _.expiration)
          validWatches = (watches -- invalid)
          outgoing = validWatches filter(_.clientId != change.source)          
        } yield {          
          // remove invalid watches
          invalid foreach (unwatch(targetId, _))
 
          // return watches that need notification
          (watchable, outgoing) 
        }

     // collate changes by watcher
      import collection.mutable.ListBuffer
      val byWatch = new MultiBuffer[PickledWatch,DataChange,ListBuffer[DataChange]]
      for {
        (change, watches) <- watched
        watch <- watches 
      } {    
        byWatch append(watch, change)
      }
     
      for {
        (watch, changes) <- byWatch
      } {
        notify(watch, changes)
      }
    }
  }
  
    
  Partitions.add(this)  // tell the manager about us
}
