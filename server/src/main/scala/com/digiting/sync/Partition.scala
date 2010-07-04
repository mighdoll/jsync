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
  class Transaction { // LATER move this so each partition subclass can implement their own
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

import Partition._
/** A storage segment of syncable objects 
 * 
 * Put() and update() operations are asynchronous and eventually consistent.  
 * so get() might not return the most recently put() data (e.g. when servers fail and recover).
 * 
 * The current implementation does not guarantee durability.  If the server crashes right after
 * put() is called, the data is lost.
 */
abstract class Partition(val partitionId:String) extends ConcretePartition with LogHelper {  
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
  
  /** name to object mapping of for well known objects in the partition.  The published
   * roots are persistent, so clients of the partition can use well known names to get
   * started with the partition data, w/o having to resort to preserving object ids, 
   * or querying, etc.
   * 
   * Eventually, objects in the partition that are not referenced directly or 
   * indirectly by one of these roots may be garbage collected by the partition.  
   * (garbage collection is NYI)
   */  
  def publish(publicName:String, root:Syncable) {
    published.create(publicName, root)
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
    
    /** notify watchers of changes */
  private def notifyChanges(tx:Transaction) {
    if (hasStorage) {
      tx.changes foreach {change => change match {
        case c:CreatedChange => // can't watch created change
        case _ =>
          val targetId = change.target.instanceId
          for {
            pickled <- get(targetId, tx) orElse 
              err("notify can't find target object for change: %s", change)
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
  
    
  Partitions.add(this)  // tell the manager about us
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
