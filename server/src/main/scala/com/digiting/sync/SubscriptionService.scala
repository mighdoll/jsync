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
import com.digiting.sync.syncable.Subscription
import com.digiting.util.LogHelper

/**  A custom service for managing client subscriptions.  The API is a shared set 
 * of Subscription objects.  The client adds and remove Subscriptions to the shared set.
 * The server responds by by streaming updates to the subscribed objects and their 
 * (deep) references.
 * 
 * The SubscriptionService API works by watching for changes to the implicitly shared
 * SyncableSet with the id: .implicit/subscriptions .
 * 
 * SubscriptionService class provides the shared set API, the actual work of watching 
 * subscription objects is managed by ActiveSubscriptions.
 */
trait SubscriptionService extends HasTransientPartition with LogHelper {
  val serviceName = "SubscriptionService"
  lazy val log = Logger(serviceName)
  val app:AppContext
  val transientPartition = app.transientPartition  
  val active = new ActiveSubscriptions(app)	            // data we're tracking for the client
  var subscriptions:SyncableSet[Syncable] = _			// set of active client subscriptions, set shared with client  

  setup()
  
  private def setup() {
    val ids = new SyncableId(app.implicitPartition.id, InstanceId("subscriptions"))
    app.withAppNoCommit {
      subscriptions = SyncManager.withNextNewId(ids) {
        new SyncableSet[Syncable]
      }
    }
    
    Observers.watch(subscriptions, serviceName, subscriptionsChanged)
  }
  
  private def subscriptionsChanged(change:ChangeDescription) {
    change match {
      case PutChange(_, newSubId, versions) => 
        for {
          newSub <- App.app.get(newSubId)
          sub <- expectSubscription(newSub) 
        } {
          log.trace("#%s subscription found: %s", app.connection.debugId, sub)
          active.subscribe(sub)
        }  
      case RemoveChange(_, oldSub, versions) =>
        log.warning("removing subscriptions is NYI")
      case _ =>
        log.warning("unexpected change to subscription: %s", change)
    }
  }
  
  private def expectSubscription(any:Any):Option[Subscription] = {
    def invalid() = {
      log.warning("invalid subscription: %s", any)
      None
    }
    def isEmpty(str:String) = {
      str match {
        case null => true
        case _ if str.length < 1 => true
        case _ => false
      }
    }
    
    any match {
      case sub:Subscription if isEmpty(sub.name) => invalid
      case sub:Subscription if isEmpty(sub.inPartition) => invalid
      case sub:Subscription => Some(sub)
      case x => 
        log.warning("unexpected object put into subscriptions set: %s", x)
        None
    }
    
  }
}  
 
