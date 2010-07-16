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
import Receiver.ReceiveMessage
import scala.collection.mutable
import util._
import Log2._


// CONSIDER -- the apps should probably be actors..
// NOTE - for now, we assume that each app context has one and only one connection
abstract class AppContext(val connection:Connection) extends HasTransientPartition 
  	with ContextPartitionGateway with HasWatches {
  implicit private lazy val log = logger("AppContext")
  def appName:String
  val remoteChange = new DynamicOnce[DataChange]
  val versioningDisabled = new DynamicVariable[Boolean](false)

  override val transientPartition = new RamPartition(connection.connectionId)
  var implicitPartition = new RamPartition(".implicit-"+ connection.connectionId) // objects known to be on both sides
  def defaultPartition:Partition = throw new ImplementationError("no partition set")
  def debugId = connection.debugId
  
  val instanceCache = new WatchedPool("AppContext")
  
  /** allows clients to subscribe to objects deeply (including references) */
  val subscriptionService = new {val app = this} with SubscriptionService

  /** override this in your app */
  def appVersion = "unspecified"  

    
  /** retrieve an object synchronously an arbitrary partition.  Stores the object in the local
    * instance cache.  */
  def get(partitionId:String, syncableId:String):Option[Syncable] = {    
    instanceCache get(partitionId, syncableId) orElse 
      get(SyncableId(PartitionId(partitionId), InstanceId(syncableId)))       
  }
  
  /** retrieve an object synchronously an arbitrary partition.  Stores the object in the local
    * instance cache.  */
  def get(ids:SyncableId):Option[Syncable] = {
    instanceCache get(ids.partitionId.id, ids.instanceId.id) orElse {
      Partitions(ids) get(ids.instanceId)
    }
  }
  
  def commit() {
    val pending = subscriptionService.active.takePending()
    val changes = instanceCache.drainChanges()
    sendPendingChanges(pending)
    commitToPartitions(changes)
  }
  
  def notifyAppWatchers() {
    
  }
      
  /** accept a protocol message for this application */
  def receiveMessage(message:Message) {
    connection.receiver ! ReceiveMessage(message)
  }
  
  /** send any queued model changes to the client in a single transaction 
   * 
   * (Note that this may be called from an arbitrary thread)
   */
  private def sendPendingChanges(pending:Seq[ChangeDescription]) {
    if (!pending.isEmpty) {
      var message = Message.makeMessage(pending)
      log.trace("sendPendingChanges #%s: queueing Pending Change: %s", connection.debugId, message.toJson)
      connection.putSendBuffer ! PutSendBuffer.Put(message)
    } else {
      log.trace("sendPendingChanges #%s: nothing Pending", connection.debugId)
    }
  }
  
  /** Run the provided function in the context of this application and commit the results */
  def withApp[T](fn: =>T):T = {
    App.current.withValue(Some(this)) {
      Observers.currentMutator.withValue(appName) {
        SyncManager.withPartition(transientPartition) { // by default create objects in the transient partition.
          val result = fn 
          commit()  // commit changes to partitions and to subscribing clients
          result
        }
      }
    }
  }
  
  /** Run the provided function in the context of this application, don't commit (used for testing) */
  def withAppNoCommit[T](fn: =>T):T = {
    App.current.withValue(Some(this)) {
      Observers.currentMutator.withValue(appName) {
        SyncManager.withPartition(transientPartition) { // by default create objects in the transient partition.
          val result = fn 
          result
        }
      }
    }
  }
  
  def withNoVersioning[T](fn: =>T):T = {
    versioningDisabled.withValue(true) {
      fn
    }
  }
  
  /** after a syncable has been changed, update the version and trigger notification */
  def updated(syncable:Syncable) (createChange: =>DataChange) {   
    val change = remoteChange.take() getOrElse createChange
    Observers.notify(change)
    if (!versioningDisabled.value) {
    	syncable.version = change.versionChange.now
    }
    trace2("updated() changeNotify(%s) versioning(%s:%s) change: %s", 
      !Observers.noticeDisabled, !versioningDisabled.value, syncable.version, change)
  }
  
  /** utility for fetching an object and running a function with the fetched object */
  private[sync] def withGetId[T](id:SyncableId)(fn:(Syncable)=>T):T = {
    get(id) map fn match {
      case Some(result) => result
      case None =>
        err2("withGetId can't find: " + id) 
        throw new ImplementationError
    }      
  }
   

  /** write pending changes to persistent storage */
  private def commitToPartitions(changes:Seq[ChangeDescription]) {
    import Matching.partialMatch
    def matchStorableChange(change:ChangeDescription):Option[StorableChange] = {
      partialMatch(change) {case storable:StorableChange => storable}
    }
    
    val storableChanges = 
      for {
        change <- changes
        storableChange <- matchStorableChange(change)
        partition <- Partitions.get(change.target.partitionId.id) orElse 
          err2("partition not found for change: %s", change.toString)
      } yield {
        (storableChange, partition)
      }
    
    // sort changes by partition
    val partitions = new MultiBuffer[Partition, StorableChange, mutable.Buffer[StorableChange]] 
    storableChanges foreach { case (storableChange, partition) => 
      partitions append(partition, storableChange) 
    }

  
    // transaction boundary within each partition
    partitions foreach { case (partition, changes) =>
      partition.withTransaction {
        changes foreach {change =>
          log.trace("commitToPartitions modify: %s", change)
          partition.modify(change)
        }
      }
    }
  }
}
   
   

