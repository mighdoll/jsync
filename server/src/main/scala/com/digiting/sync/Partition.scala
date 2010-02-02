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
import scala.actors.Actor
import scala.actors.Actor._
import JsonObject._
import collection.mutable
import net.lag.logging.Logger
import com.digiting.util.LogHelper
import SyncManager.withGetId
import scala.collection.mutable.ListBuffer


object Partition {
  class Transaction {
    val id = RandomIds.randomUriString(8)
    private[this] val changes = new ListBuffer[DataChange]
    def +=(change:DataChange) {
      changes += change
    }
    def updates:Iterable[DataChange] = changes.toList
  }
  class InvalidTransaction(message:String) extends Exception(message) {
    def this() = this("")
  }
}

/** A storage segment of syncable objects 
 * 
 * Put() and update() operations are asynchronous and eventually consistent.  
 * so get() might not return the most recently put() data (e.g. when servers fail and recover).
 * 
 * The current implementation does not guarantee durability.  If the server crashes right after
 * put() is called, the data is lost.
 */
import Partition._
abstract class Partition(val partitionId:String) {  
  private val currentTransaction = new DynamicVariable[Option[Transaction]](None)
  
  /** all CRUD operations should be called within a transaction */
  def withTransaction[T](fn: =>T):T = {
    val tx = new Transaction
    currentTransaction.withValue(Some(tx)) {
      val result = fn
      commit(tx)
      result
    }
  }

  /** commit pending updates */
  def commit(transaction:Transaction)
  
  /** toss pending changes */
  def rollback(transaction:Transaction)
  
  /** fetch an object or a collection */
  def get[T <: Syncable](instanceId:String):Option[T] = {
    inTransaction {tx => 
      for {
        pickled:Pickled[_] <- get(instanceId, tx)
        syncable = pickled.unpickle
      } yield syncable.asInstanceOf[T]
    }
  }
  
  /** create, update or delete an object or a collection*/
  def update(change:DataChange):Unit  = {
    inTransaction {tx => update(change, tx)}    
  }
  
  /** subclass should implement */
  private[sync] def update(change:DataChange, tx:Transaction):Unit  
  
  /** subclass should implement */
  private[sync] def get[T <: Syncable](instanceId:String, tx:Transaction):Option[Pickled[T]]
  
  /** verify that we're currently in a valid transaction*/
  private[this] def inTransaction[T](fn: (Transaction)=>T):T =  {
    currentTransaction value match {
      case Some(tx:Transaction) => 
        fn(tx)
      case None => 
        throw new InvalidTransaction("no current transaction")
    }
  }
  
  
  /** name to object mapping of for well known objects in the partition.  The published
   * roots are persistent, so clients of the partition can use well known names to get
   * started with the partition data, w/o having to resort to querying, etc.
   *
   * (someday we may garbage collect objects that are not collected to these roots..)
   */
  val published = new PublishedRoots(this)
  
  /** debug utility, prints contents to log */
  def debugPrint() {}
  
  /** destory this partition and its contents */
  def deletePartition() {
    Partitions.remove(partitionId)
    deleteContents()
  }
  
  /** erase all of the data in the partition, including the PublishedRoots */
  def deleteContents():Unit

  
  Partitions.add(this)  // tell the manager about us
  
  // LATER make this is a transactional interface, see Partition2 for an early sketch
}

/** this is a trick to allow a simulated client to refer to a Partition instance 
  * with the name ".transient".  LATER we should change the SyncableIdentity to use a string
  * for the partition id, rather than the partition reference.
  */
object TransientPartition extends FakePartition(".transient")

class FakePartition(partitionId:String) extends Partition(partitionId) {
  def deleteContents() {}
  def get[T <: Syncable](instanceId:String, tx:Transaction):Option[Pickled[T]] = None
  def update(change:DataChange, tx:Transaction):Unit  = {}
  def commit(tx:Transaction) {}
  def rollback(tx:Transaction) {}
}


class RamPartition(partId:String) extends Partition(partId) with LogHelper {
  val log = Logger("RamPartition")
  val store = new mutable.HashMap[String, Pickled[_]]
  def commit(tx:Transaction) {}
  def rollback(tx:Transaction) {}
  
  def get[T <: Syncable](instanceId:String, tx:Transaction):Option[Pickled[T]] = {
    store get instanceId map {_.asInstanceOf[Pickled[T]]}    
  }
  
  def update(change:DataChange, tx:Transaction) = change match {
    case created:CreatedChange[_] => 
      store += (created.target.instanceId -> created.pickled)
    case prop:PropertyChange =>
      throw new NotYetImplemented
    case deleted:DeletedChange =>
      throw new NotYetImplemented
    case _ => // other changes should already be applied to objects in RAM
  }
  
  def deleteContents() {
    for {(id, pickled) <- store} {
      log.trace("deleting: %s", pickled)
      store -= (id)
    }
  }

  override def debugPrint() {
    for {(_,value) <- store} {
      log.info("  %s", value.toString)
    }
  }
}

import collection._
object Partitions {
  val log = Logger.get("Partitions")
  val localPartitions = new mutable.HashMap[String,Partition]
  def get(name:String):Option[Partition] = localPartitions get name
  def add(partition:Partition) = localPartitions += (partition.partitionId -> partition)
  
  def getMust(name:String):Partition = {
    get(name) getOrElse {
      log.error("user Partition %s not found", name)
      throw new ImplementationError
    }
  }
  
  def remove(name:String) = {
    localPartitions -= name
  }
  
  add(new RamPartition("default"))	// CONSIDER don't really want a default partition do we?  is this needed?
  
  // LATER, create strategy for handling remote partitions
}
