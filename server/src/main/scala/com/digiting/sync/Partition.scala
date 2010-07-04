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
import collection.mutable
import com.digiting.util._
import RandomIds.randomUriString

object Partition extends LogHelper {
  lazy val log = logger("Partition(Obj)")
  class Transaction { // SOON move this so each partition subclass can implement their own
    val id = randomUriString(8)
    val changes = new mutable.ListBuffer[DataChange]
  }
  
  class InvalidTransaction(message:String) extends Exception(message) {
    def this() = this("")
  }
}

case class PartitionId(val id:String) {
  override def toString:String = id
}

case class InstanceId(val id:String) {
  override def toString:String = id
}

trait PartitionNotification extends Partition {
  def notify(watch: PickledWatch)
}

import Partition._

/** A storage segment of syncable objects 
 * 
 * Put() and update() operations are asynchronous and eventually consistent.  
 * so get() might not return the most recently put() data (e.g. when servers fail and recover).
 * 
 * The current implementation does not guarantee durability.  If the server crashes right after
 * put() is called, the data is lost.
 */
abstract class Partition(val partitionId:String) extends LogHelper {  
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

  private def notifyChanges(tx:Transaction) {
    if (hasStorage) {
      tx.changes foreach {change => change match {
        case c:CreatedChange => // can't watch created change
        case _ =>
          val targetId = change.target.instanceId
          for {
            pickled <- get(targetId, tx) orElse {
              Console print "foo"
              err("notify can't find target object for change: %s", change)
              Thread.sleep(10000)
              None
            }
            watches = pickled.watches
          } {
            val invalid = watches filter(System.currentTimeMillis > _.expiration)
            val validWatches = watches -- invalid
            invalid foreach (unwatch(targetId, _))
            // call matching functions
            validWatches filter(_.clientId != change.source) foreach { watch =>
            	notify(watch, change, tx)
            } 
          }
        }
      }      
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
  
    /** create, update or delete an object or a collection */
  def modify(change:DataChange) { 
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
  
  /* subclasses should implement these */
  private[sync] def modify(change:DataChange, tx:Transaction):Unit 
  private[sync] def get(id:InstanceId, tx:Transaction):Option[Pickled]
  private[sync] def watch(id:InstanceId, watch:PickledWatch, tx:Transaction):Unit  
  private[sync] def unwatch(id:InstanceId, watch:PickledWatch, tx:Transaction):Unit    
  private[sync] def commit(tx:Transaction):Boolean 
  private[sync] def notify(watch:PickledWatch, change:DataChange, tx:Transaction) {}


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
  
  /** name to object mapping of for well known objects in the partition.  The published
   * roots are persistent, so clients of the partition can use well known names to get
   * started with the partition data, w/o having to resort to preserving object ids, 
   * or querying, etc.
   * 
   * Eventually, objects in the partition that are not referenced directly or 
   * indirectly by one of these roots may be garbage collected by the partition.  
   * (This is NYI)
   */
  
  def publish(publicName:String, root:Syncable) {
    published.create(publicName, root)
  }
  
  def publish(publicName:String, generator: ()=>Option[Syncable]) {
    published.createGenerated(publicName, generator)
  }
  
  /** debug utility, prints contents to log */
  def debugPrint() {}
  
  /** destroy this partition and its contents */
  def deletePartition() {
    Partitions.remove(id.id)
    deleteContents()
  }
  
  /** erase all of the data in the partition, including the PublishedRoots */
  def deleteContents():Unit
  
  /** true if this partition actually stores objects (false for .transient) */
  def hasStorage = true
  
  Partitions.add(this)  // tell the manager about us
}

/** this is a trick to allow a simulated client to refer to a Partition instance 
  * with the name ".transient".  LATER we should change the SyncableIdentity to use a string
  * for the partition id, rather than the partition reference (...SyncableIdentity is gone now.)
  */
object TransientPartition extends FakePartition(".transient")

class FakePartition(partitionId:String) extends Partition(partitionId) {
  def get(instanceId:InstanceId, tx:Transaction):Option[Pickled] = None
  def modify(change:DataChange, tx:Transaction):Unit  = {}
  def commit(tx:Transaction) = true
  private[sync] def watch(id:InstanceId, watch:PickledWatch, tx:Transaction) {}
  private[sync] def unwatch(id:InstanceId, watch:PickledWatch, tx:Transaction) {}
  override def hasStorage = false
  def deleteContents() {}
}

import collection.mutable.HashMap

// SOON change get(), remove() to use PartitionId
object Partitions extends LogHelper {
  val log = logger("Partitions")
  val localPartitions = new HashMap[String, Partition]    // Synchronize?
  def get(name:String):Option[Partition] = localPartitions get name
  def add(partition:Partition) = localPartitions += (partition.id.id -> partition)
  
  def getMust(name:String):Partition = {
    get(name) getOrElse {
      abort("user Partition %s not found", name)
    }
  }
  
  def remove(name:String) = {
    localPartitions -= name
  }
    
  // LATER, create strategy for handling remote partitions
}
