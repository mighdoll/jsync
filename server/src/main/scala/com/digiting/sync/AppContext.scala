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
import com.digiting.util._
import scala.collection.mutable

/** thread local access to the currently running app context */
object App extends LogHelper {
  val log = logger("App(Obj)")
  val current = new DynamicVariable[Option[AppContext]](None)
  def currentAppName:String = current.value match {
    case Some(app) => app.appName
    case _ => "<no-current-app>"
  }
  
  def withTransientPartition[T](fn: =>T):T = {
    current.value match {
      case Some(app:AppContext) => app.withTransientPartition(fn)
      case _ => 
        throw new ImplementationError()
    }  
  }
  def app:AppContext = current.value getOrElse {abort("App.app() - no current app")}
}

trait HasTransientPartition { 
  val transientPartition:Partition

  def withTransientPartition[T] (fn: =>T):T = {
    SyncManager.currentPartition.withValue(transientPartition) {
      fn
    }
  }
  
}

class GenericAppContext(connection:Connection) extends AppContext(connection) {
  val appName = "generic-app-context"
}

object TempAppContext {
  def apply(name:String):AppContext = {
		new {val appName = name} with AppContext(new Connection(name + "-connection"))
  }
}

/** App with the ability to connect syncable message queues to annotated service endpoints.  */
abstract class RichAppContext(connection:Connection) extends AppContext(connection) with ImplicitServices

// CONSIDER -- the apps should probably be actors..
// NOTE - for now, we assume that each app context has one and only one connection
abstract class AppContext(val connection:Connection) extends HasTransientPartition 
  	with ContextPartitionGateway with LogHelper {
  override val log = logger("AppContext")
  def appName:String
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
    instanceCache get(partitionId, syncableId) orElse {
      val foundOpt = Partitions.get(partitionId) match {
        case Some(partition) => partition.get(InstanceId(syncableId)) 
        case _ =>
          log.error("unexpected partition in Syncables.get: " + partitionId)
          None        
      }
      foundOpt map (instanceCache put _)  
      foundOpt
    }   
  }
  
  /** retrieve an object synchronously an arbitrary partition.  Stores the object in the local
    * instance cache.  */
  def get(ids:SyncableId):Option[Syncable] = {
    instanceCache get(ids.partitionId.id, ids.instanceId.id) orElse {
      Partitions.get(ids.partitionId.id) orElse {
        err("no partition found for: %s", ids.toString)
      } flatMap {partition =>
        partition get ids.instanceId map {found =>
          instanceCache put found
          found
        }
      }
    }
  }

  def commit() {
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
  


  
  /** utility for fetching an object and running a function with the fetched object */
  private[sync] def withGetId[T](id:SyncableId)(fn:(Syncable)=>T):T = {
    get(id) map fn match {
      case Some(result) => result
      case None =>
        err("withGetId can't find: " + id) 
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
          err("partition not found for change: %s", change.toString)
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
