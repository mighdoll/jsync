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
import RandomIds.randomId
import Log2._


// CONSIDER -- the apps should probably be actors..
// NOTE - for now, we assume that each app context has one and only one connection
abstract class AppContext(val connection:Connection) extends HasTransientPartition 
  	with ContextPartitionGateway with HasWatches with AppContextActor {
  implicit private lazy val log = logger("AppContext")
  
  def appName:String
  val remoteChange = new DynamicOnce[DataChange]
  val versioningDisabled = new DynamicVariable[Boolean](false)
  val appId = new AppId("a"+randomId(10))

  override val transientPartition = new RamPartition(connection.connectionId)
  
  var implicitPartition = new RamPartition(".implicit-"+ connection.connectionId) // objects known to be on both sides
  def defaultPartition:Partition = throw new ImplementationError("no partition set")
  
  /** handy id for logging */
  def debugId = connection.debugId

  /** syncable objects cached here in context.  includes all the syncables in an active client subscription. 
   * changes to these syncables are observed and sent to the partitions for persistent storage */
  val instanceCache = new WatchedPool("AppContext")
  
  /** enable/disable ChangeDescriptions */
  private val generateChanges = new DynamicVariable[Boolean](true)

  /** allows clients to subscribe to objects deeply (including references) */
  val subscriptionService = new {val app = this} with SubscriptionService

  /** override this in your app */
  def appVersion = "unspecified"  
  
  /** retrieve an object synchronously from instance cache or from its home partition.  */
  def get(id:SyncableId):Option[Syncable] = {
    instanceCache get(id) orElse {
      Partitions(id) get(id.instanceId) // SyncManager.created() will put this newly fetched object in the pool
    }
  }
  
  def commit() {     
    trace2("#%s commit()", debugId)
    notifyWatchers() // notify internal apps, possibly generating more changes
    val pending = subscriptionService.active.takePending()
    val changes = instanceCache.drainChanges()
    sendPendingChanges(pending)
    commitToPartitions(changes)
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
      trace2("#%s sendPendingChanges: queueing message: %s", connection.debugId, message.toJson)
      connection.putSendBuffer ! PutSendBuffer.Put(message)
    } else {
      trace2("#%s sendPendingChanges: nothing Pending", connection.debugId)
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
    
  /** enables/disables generation of ChangeDescriptions while executing a function */
  def enableChanges[T](enable:Boolean)(fn: =>T):T = {
    generateChanges.withValue(enable) {fn}
  }
  
  /** after a syncable has been changed, update the version and trigger notification */
  def updated(syncable:Syncable) (createChange: =>DataChange) {   
    if (generateChanges.value) {
      val proposed = createChange 
      val change = remoteChange.take() map {remote =>
        assert(remote.getClass == proposed.getClass) 
        remote
      } getOrElse createChange
      Observers.notify(change)
      syncable.version = change.versionChange.now
      trace2("#%s updated() changeNotify(%s) change: %s", debugId,
        !Observers.noticeDisabled, change)
    } else {
      trace2("#%s updated() change generation disabled for: %s", debugId, syncable)
    }
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
          err2("#%s partition not found for change: %s", debugId, change.toString)
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
      partition.withTransaction(appId) {
        changes foreach {change =>
          trace2("#%s commitToPartitions modify: %s", debugId, change)
          partition.modify(change)
        }
      }
    }
  }
}
   
   

