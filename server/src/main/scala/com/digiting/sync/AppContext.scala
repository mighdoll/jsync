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
import net.lag.logging.Logger
import scala.util.DynamicVariable
import Receiver.ReceiveMessage

/** thread local access to the currently running app context */
object App {
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
}

trait HasTransientPartition {
  val transientPartition:Partition

  def withTransientPartition[T] (fn: =>T):T = {
    SyncManager.currentPartition.withValue(transientPartition) {
      fn
    }
  }
  
}

class RichAppContext(connection:Connection) extends AppContext(connection) with ImplicitServices

// CONSIDER -- the apps should probably be actors..
// for now, we assume that each app context has one and only one connection
class AppContext(val connection:Connection) extends HasTransientPartition {
  private val log = Logger("AppContext")
  val appName = "server-application"
  override val transientPartition = new RamPartition(connection.connectionId)
  var implicitPartition = new RamPartition(".implicit-"+ connection.connectionId) // objects known to be on both sides
  def defaultPartition:Partition = throw new ImplementationError("no partition set") 		
  
  /** provides a shared*/
  val subscriptionService = new {val app = this} with SubscriptionService

  /** override this the app */
  def appVersion = "unspecified"  
  
  // when we commit, send changes to the client too
  watchCommit(sendPendingChanges)
      
  def commit() {
    SyncManager.instanceCache.commit();
  }
  
      
  /** LATER move the instance cache out of the manager, and make it per app */
  def watchCommit(func:(Seq[ChangeDescription])=>Unit) {
  	SyncManager.instanceCache.watchCommit(func)
  }
  
  /** accept a protocol message for this application */
  def receiveMessage(message:Message) {
    connection.receiver ! ReceiveMessage(message)
  }
  
  /** send any queued model changes to the client in a single transaction 
   * 
   * (Note that this may be called from an arbitrary thread)
   */
  private def sendPendingChanges(ignored:Seq[ChangeDescription]) = {
    val pending = subscriptionService.active.takePending()
    if (!pending.isEmpty) {
      var message = Message.makeMessage(pending)
      log.trace("sendPendingChanges #%s: queueing Pending Change: %s", connection.debugId, message.toJson)
      connection.putSendBuffer ! PutSendBuffer.Put(message)
    } else {
      log.trace("sendPendingChanges #%s: nothing Pending", connection.debugId)
    }
  }
  
  /** Run the provided function in the context of this application */
  def withApp[T](fn: =>T):T = {
    App.current.withValue(Some(this)) {
      Observers.currentMutator.withValue(appName) {
        val result = fn 
        commit()  // commit changes to partitions and to subscribing clients
        result
      }
    }
  }
  

  
}
