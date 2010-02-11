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
import com.digiting.util._
import java.io.Serializable

object Partition {
  class Transaction {
    val id = RandomIds.randomUriString(8)
  }
  class InvalidTransaction(message:String) extends Exception(message) {
    def this() = this("")
  }
}

case class PartitionId(val id:String) {
  override def toString:String = id
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
abstract class Partition(val id:String) {  
  val log1 = Logger("Partition")
  val partitionId = PartitionId(id)
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
  def get(instanceId:String):Option[Syncable] = {
    withForcedTransaction {
      val syncable = 
        inTransaction {tx => 
          for {
            pickled <- get(instanceId, tx)
            syncable:Syncable = pickled.unpickle // loads referenced objects too
          } yield syncable
        }
      log1.trace("#%s get(%s) = %s", partitionId, instanceId, syncable)
      syncable
    }
  }
  
  /** create a single operation transaction if necessary */
  private def withForcedTransaction[T](fn: =>T):T = {
    currentTransaction value match {
      case Some(_) => 
        fn
      case None =>
        currentTransaction.withValue(Some(new Transaction)) {
          fn
        }        
    }
  }
  
  /** create, update or delete an object or a collection*/
  def update(change:DataChange):Unit  = {
    inTransaction {tx => 
      log1.trace("#%s update(%s)", partitionId, change)
      update(change, tx)
    }    
  }
  
  /* subclasses should implement these */
  private[sync] def update(change:DataChange, tx:Transaction):Unit  
  private[sync] def get(instanceId:String, tx:Transaction):Option[Pickled]

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
   * objects in the partition that are not referenced directly or indirectly by one
   * of these roots may be garbage collected by the partition.
   */
  private[sync] val published = new PublishedRoots(this)
  
  def publish(publicName:String, root:Syncable) {
    published.create(publicName, root)
  }
  def publishGenerator(publicName:String, generator: ()=>Option[Syncable]) {
    published.createGenerated(publicName, generator)
  }
  
  /** debug utility, prints contents to log */
  def debugPrint() {}
  
  /** destory this partition and its contents */
  def deletePartition() {
    Partitions.remove(partitionId.id)
    deleteContents()
  }
  
  /** erase all of the data in the partition, including the PublishedRoots */
  def deleteContents():Unit
  
  Partitions.add(this)  // tell the manager about us
}

/** this is a trick to allow a simulated client to refer to a Partition instance 
  * with the name ".transient".  LATER we should change the SyncableIdentity to use a string
  * for the partition id, rather than the partition reference.
  */
object TransientPartition extends FakePartition(".transient")

class FakePartition(partitionId:String) extends Partition(partitionId) {
  def get(instanceId:String, tx:Transaction):Option[Pickled] = None
  def update(change:DataChange, tx:Transaction):Unit  = {}
  def commit(tx:Transaction) {}
  def rollback(tx:Transaction) {}
  def deleteContents() {}
}

import collection.mutable.HashMap
object Partitions {
  val log = Logger("Partitions")
  val localPartitions = new HashMap[String, Partition]    // Synchronize?
  def get(name:String):Option[Partition] = localPartitions get name
  def add(partition:Partition) = localPartitions += (partition.partitionId.id -> partition)
  
  def getMust(name:String):Partition = {
    get(name) getOrElse {
      log.error("user Partition %s not found", name)
      throw new ImplementationError
    }
  }
  
  def remove(name:String) = {
    localPartitions -= name
  }
    
  // LATER, create strategy for handling remote partitions
}
