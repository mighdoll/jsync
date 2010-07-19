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
import Log2._
import Partition.Transaction
import Partition.InvalidTransaction

/** A storage segment of syncable objects 
 * 
 * Put() and update() operations are asynchronous and eventually consistent.  
 * so get() might not return the most recently put() data (e.g. when servers fail and recover).
 * 
 * The current implementation does not guarantee durability.  If the server crashes right after
 * put() is called, the data is lost.
 */
abstract class Partition(val partitionId:String) extends ConcretePartition { 
  implicit private lazy val log2 = logger("Partition")
  val id = PartitionId(partitionId)
  private val currentTransaction = new DynamicVariable[Option[Transaction]](None)
  private[sync] val published = new PublishedRoots(this)
  
  /** all get,modify,watch operations should be called within a transaction */
  def withTransaction[T](sender:SyncNode)(fn: =>T):T = {
    val tx = new Transaction(sender)
    currentTransaction.withValue(Some(tx)) {
      val result = fn
      commit(tx) && {notifyChanges(tx); true}
      result
    }
  }
  
  def withDebugTransaction[T](sender:SyncNode)(fn: (Transaction)=>T):T = {
    withTransaction(sender:SyncNode) {
      currentTransaction.value map {tx =>
        fn(tx)
      } getOrElse abort2("")
    }
  }

  val fakeSender = AppId("autoGet")
  /** Fetch an object or a collection.  
   * (Creates an implicit transaction if none is currently active) */
  def get(instanceId:InstanceId):Option[Syncable] = {
    val syncable = 
      autoTransaction(fakeSender) { tx =>
        for {
          pickled <- get(instanceId, tx)
          syncable:Syncable = pickled.unpickle // loads referenced objects too
        } yield syncable
      }
    trace2("#%s get(%s) = %s", partitionId, instanceId, syncable)
    syncable
  }
  
    /** create, update, delete or observer an object or a collection */
  def modify(change:StorableChange) { 
    expectTransaction {tx => 
      trace2("#%s update(%s)", partitionId, change)
      modify(change, tx)
      tx.changes += change
    }    
  }

  
//  /** 
//   * Observe an object for changes.
//   * 
//   * After the specified duration in milliseconds, the watch is discarded.
//   */
//  def watch(id:InstanceId, pickledWatch:PickledWatch) {
//    expectTransaction {tx =>
//      watch(id, pickledWatch, tx)
//    }
//  }
//  
//  /** unregister a previously registered watch */
//  def unwatch(id:InstanceId, pickledWatch:PickledWatch) {
//    expectTransaction {tx =>
//      unwatch(id, pickledWatch, tx)
//    }    
//  }
  
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
  def publish(publicName:String) (generator: =>Option[Syncable]) {
    published.createGenerated(publicName, () =>generator)
  }
    
  /** destroy this partition and its contents */
  def deletePartition() {
    Partitions.remove(id.id)
    deleteContents()
  }
  
  /** verify that we're currently in a valid transaction*/
  private def expectTransaction[T](fn: (Transaction)=>T):T =  {
    currentTransaction value match {
      case Some(tx:Transaction) => 
        fn(tx)
      case None => 
        throw new InvalidTransaction("no current transaction")
    }
  }
    
  /** create a single operation transaction if necessary */
  private def autoTransaction[T](sender:SyncNode)(fn:(Transaction) =>T):T = {
    currentTransaction value match {
      case Some(tx) => 
        fn(tx)
      case None =>
        withTransaction(sender) {
          expectTransaction(fn)
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
        case obs:ObserveChange => None
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
          outgoing = validWatches filter(_.watcherId != change.source)          
        } yield {          
          // remove invalid watches
          invalid foreach (unwatch(change.target, _))
 
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
      
      byWatch foreach {case (watch, changes) => notify(watch, changes)}
    }
  }
  
  private def unwatch(id:SyncableId, pickledWatch:PickledWatch) {
    // we're in the midst of completing a transaction as this is called.
    // so we can just apply the change directly to the store w/o creating a new tx..
	  modify(EndObserveChange(id, pickledWatch))    
  }
  
  protected def withPickled[T](instanceId:InstanceId, tx:Transaction)(fn:(Pickled)=>T):T = {
    get(instanceId, tx) match {
      case Some(pickled) => 
        fn(pickled)
      case _ =>
        abort2("watch() can't find instance %s", instanceId)
    }
  }  

  /** SOON- this should be refactored.  It's on the client side when we support remote partitions, and
   * most of Partition is on the server side.  Regardless it should be in a separate trait, 
   * (moved here trying to work around spurious verify errors in eclipse.  retry after SCALA 2.8) */
  import RandomIds.randomUriString  
  import java.util.concurrent.ConcurrentHashMap
  import RamWatches.PartitionWatchFn
  
  val watchFns = new ConcurrentHashMap[RequestId, PartitionWatchFn]
  
  def pickleWatch(duration:Int)(fn:PartitionWatchFn):PickledWatch = {
    val requestId = RequestId(randomUriString(8))
    watchFns.put(requestId, fn)
    new PickledWatch(App.app.appId, requestId, System.currentTimeMillis + duration)
  }
      
  protected[sync] def notify(pickledWatch:PickledWatch, changes:Seq[DataChange]) {
    val fn = watchFns get(pickledWatch.requestId)
    if (fn != null) {
      fn(changes)
    } else {
      err2("notify() can't find fn for %s  for change:%s", pickledWatch, changes)
    }   
  }
      
  Partitions.add(this)  // tell the manager about us
}
