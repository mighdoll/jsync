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
import scala.collection.mutable.Buffer
import com.digiting.util.MultiBuffer
import collection.mutable
import collection.mutable.HashSet
import scala.collection.mutable.MultiMap

object Partition {
  class Transaction {
    val id = RandomIds.randomUriString(8)
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
  val log1 = Logger("Partition")
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
    withForcedTransaction {
      val syncable = 
        inTransaction {tx => 
          for {
            pickled:Pickled[_] <- get(instanceId, tx)
            syncable = pickled.unpickle
          } yield syncable.asInstanceOf[T]
        }
      log1.trace("#%s get(%s) = %s", partitionId, instanceId, syncable)
      syncable
    }
  }
  
  def getSeqMembers(instanceId:String):Option[Seq[SyncableReference]] = {
    withForcedTransaction {
      inTransaction {tx => getSeqMembers(instanceId, tx)}
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
  
  /* subclass should implement these */
  private[sync] def update(change:DataChange, tx:Transaction):Unit  
  private[sync] def get[T <: Syncable](instanceId:String, tx:Transaction):Option[Pickled[T]]
  private[sync] def getSeqMembers(instanceId:String, tx:Transaction):Option[Seq[SyncableReference]]

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
  def getSeqMembers(instanceId:String, tx:Transaction):Option[Seq[SyncableReference]] = None
  def update(change:DataChange, tx:Transaction):Unit  = {}
  def commit(tx:Transaction) {}
  def rollback(tx:Transaction) {}
}


class RamPartition(partId:String) extends Partition(partId) with LogHelper {
  val log = Logger("RamPartition")
  val store = new mutable.HashMap[String, Pickled[Syncable]]
  val seqMembers = new mutable.HashMap[String, Buffer[SyncableReference]] 
    with MultiBuffer[String, SyncableReference]
  val setMembers = new mutable.HashMap[String, mutable.Set[SyncableReference]] 
    with MultiMap[String, SyncableReference]
  def commit(tx:Transaction) {}
  def rollback(tx:Transaction) {}
  
  def get[T <: Syncable](instanceId:String, tx:Transaction):Option[Pickled[T]] = {    
    store get instanceId map {_.asInstanceOf[Pickled[T]]}    
  }
  
  def update(change:DataChange, tx:Transaction) = change match {
    case created:CreatedChange[_] => 
      put(created.pickled)
    case prop:PropertyChange =>
      get(prop.target.instanceId, tx) orElse {  
        err("target of property change not found: %s", prop) 
      } foreach {pickled =>
        pickled.update(prop)
        put(pickled)
      }
    case deleted:DeletedChange =>
      throw new NotYetImplemented
    case clear:ClearChange =>      
      // we don't know the type of the target, so clear 'em all.  CONSIDER: should dataChange.target a SyncableReference?
      seqMembers -= clear.target.instanceId
      setMembers -= clear.target.instanceId
    case put:PutChange =>
      setMembers.add(put.target.instanceId, put.newVal)
    case remove:RemoveChange =>
      setMembers.remove(remove.target.instanceId, remove.oldVal)
    case move:MoveChange =>
      val moving = seqMembers.remove(move.target.instanceId, move.fromDex)
      seqMembers.insert(move.target.instanceId, moving, move.toDex)
    case insertAt:InsertAtChange =>
      seqMembers.insert(insertAt.target.instanceId, insertAt.newVal, insertAt.at)
    case removeAt:RemoveAtChange =>
      seqMembers.remove(removeAt.target.instanceId, removeAt.at)
      throw new NotYetImplemented
    case putMap:PutMapChange =>
      throw new NotYetImplemented
    case removeMap:RemoveMapChange =>
      throw new NotYetImplemented
  }
  
  private[this] def put[T <: Syncable](pickled:Pickled[T]) {
    store += (pickled.reference.instanceId -> pickled.asInstanceOf[Pickled[Syncable]])
  }
  
  def getSeqMembers(instanceId:String, tx:Transaction):Option[Seq[SyncableReference]] = {    
    seqMembers get instanceId
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
