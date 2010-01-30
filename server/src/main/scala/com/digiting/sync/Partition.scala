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


/** A storage segment of syncable objects 
 * 
 * Put() and update() operations are asynchronous and eventually consistent.  
 * so get() might not return the most recently put() data (e.g. when servers fail and recover).
 * 
 * The current implementation does not guarantee durability.  If the server crashes right after
 * put() is called, the data is lost.
 */
abstract class Partition(val partitionId:String) {
  def get(instanceId:String):Option[Syncable] 
  def put(syncable:Syncable):Unit
  def delete(instanceId:String):Unit
  def update(change:ChangeDescription):Unit  
  val published = new PublishedRoots(this)
  def debugPrint() {}
  def deletePartition() {
    Partitions.remove(partitionId)
    deleteContents()
  }
  def deleteContents():Unit

  Partitions.add(this)
  
  // LATER make this is a transactional interface, see Partition2 for an early sketch
}

/** this is a trick to allow a simulated client to refer to a Partition instance 
  * with the name ".transient".  LATER we should change the SyncableIdentity to use a string
  * for the partition id, rather than the partition reference.
  */
object TransientPartition extends FakePartition(".transient")

class FakePartition(partitionId:String) extends Partition(partitionId) {
  def deleteContents() {}
  def get(instanceId:String):Option[Syncable] = None
  def put(syncable:Syncable):Unit = {}
  def delete(instanceId:String):Unit = {}
  def update(change:ChangeDescription):Unit  = {}
}


class RamPartition(partId:String) extends Partition(partId) with LogHelper {
  val log = Logger("RamPartition")
  val store = new mutable.HashMap[String,Syncable]
  
  def get(instanceId:String):Option[Syncable] = store get instanceId 
  def put(syncable:Syncable) = store put (syncable.id, syncable)
  def delete(instanceId:String) = store -= instanceId
  def update(change:ChangeDescription) = change match {
    case created:CreatedChange[_] => 
      // TODO change CreatedChange to deserialize the object
      withGetId(created.target) {put}
    case _ => // other changes should already be applied to objects in RAM
  }
  
  def deleteContents() {
    for {(_, syncable) <- store} {
      if (syncable.partition == this) {
        log.trace("deleting: %s", syncable)
        delete(syncable.id)
        SyncManager.instanceCache.remove(syncable)
      }      
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
