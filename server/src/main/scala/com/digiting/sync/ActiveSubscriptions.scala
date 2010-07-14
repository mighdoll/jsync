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
import actors.Actor._
import actors.Actor
import collection._
import com.digiting.sync.syncable.Subscription
import net.lag.logging.Logger
import com.digiting.util.LogHelper

/**
 * Maintains a set of observations on the syncable object trees referenced by a given
 * connection's subscriptions.  
 * 
 * The changes to subscribed objects are queued.  Clients of this class can retrieve 
 * the queued changes via takePending().
 */
class ActiveSubscriptions(app:AppContext) extends Actor with LogHelper {
  val log = Logger("ActiveSubscriptions")
  val subscriptions = new mutable.HashSet[Syncable] 		 // active subscription roots
  val deepWatches = new mutable.HashSet[DeepWatch] 		 	 // active subscription root deep watches
  val pendingChanges = new mutable.Queue[ChangeDescription]  // model changes waiting for a commit.  
  
  start
  
  log.trace("#%s created", app.debugId)
  
  /** watch the set of objects referenced in the subscription request.  Modify the client's 
   * subscription request object by seeing its root field.  The change to the request object
   * propogates back to the client */
  def subscribe(sub:Subscription) {
    Partitions get sub.inPartition match {
      case Some(partition) =>
        partition.published find sub.name match {
          case Some(root) => 
            log.trace("#%s subscribe to: %s,  root: %s", app.debugId, sub.name, root)
            subscriptions += root
            subscribeRoot(sub)
            Observers.currentMutator.withValue("ActiveSubscriptions") {
              sub.root = root;	// update will propogate to client
            }
          case _ =>
            log.error("can't find subscription name: " + sub.name + ", subscription: " + sub)          
        }
      case None => log.error("subscribe: partition not found: " + sub.inPartition)
    }
  }

  def unsubscribe(sub:Subscription) {
    subscriptions -= sub
    unsubscribeRoot(sub)
  }
  
  def unsubscribeRoot(root:Syncable) {
    deepWatches find {deep:DeepWatch => deep.root == root} map {deep =>
      log.trace("unsubscribe deepwatch: %s", deep)
      deepWatches -= deep
      deep disable
    } orElse {
      err("deepWatch not found for root: " + root)
    }    
  }
  
  /** watch the given root object */
  def subscribeRoot(root:Syncable) {
    val deep = Observers.watchDeep(root, treeChanged, treeChanged, this)
    deepWatches += deep
    log.trace("#%s, subscribeRoot() subscribed %s deepwatch: %s", app.debugId, root, deep)
  }
    
  /** temporarily subscribe to a root object so that changes to that an objects 
   * reference tree will be sent to the client */
  def withTempRootSubscription[T](root:Syncable)(fn: => T) = {
    subscribeRoot(root)
    val result = 
      try {
        fn      
      } finally {
        unsubscribeRoot(root)
      }
    
    result
  }
  
 
  /** remember a change that we'll later send to the client */
  private def treeChanged(change:ChangeDescription):Unit = {      
    change match {
      case _ if change.source == app.connection.connectionId =>
        log.trace("#%s not queueing change: originated from client: %s", 
    	  app.connection.debugId, change)
      case begin:BeginWatch if begin.watcher.watchClass == this =>  
      // The game here is that makeMessage converts BeginWatch into a Sync send to
      // the client, which we don't want to do for the subscription object itself
      // since it came from the client.  
      //  (someday SOON we should untwist this mechanism)
      	app.observeInPartition(begin.target)
        App.app.get(begin.newValue) match {
          case Some(sub:Subscription) => 
          case _ =>
            queueChange(change)
        }
      // This code and the test above assumes that BeginWatch messages are 
      // sent to completely other DeepWatch subscribers, and we need to filter them out.
      // I don't think that's actually the case though, SOON try removing it.
      case watch:DeepWatchChange if watch.watcher.watchClass == this =>
        queueChange(change)
      case watch:DeepWatchChange =>
        log.trace("#%s not queueing watch change from another watch or deepwatch: #%s.  change: %s", 
                  app.debugId, watch.watcher.debugId, change)
      case _ =>  
        queueChange(change)
      }
  }
  
  private def queueChange(change:ChangeDescription) {
    log.trace("#%s change queued: %s", app.debugId, change)
    this ! change
  }

  
  /** for debugging */
  def print {
    for (change <- pendingChanges) {
      Console println ("pending: " + change)
    }    
  }
  
  /** get pending changes, return empty Seq if none are available */
  def takePending():Seq[ChangeDescription] = {
    val result = this !? TakePending()
    result.asInstanceOf[Seq[ChangeDescription]]
  }

  private case class TakePending()
  
  def act = {	
    loop {
      react {
        case change:ChangeDescription => pendingChanges += change
        case TakePending() => 
          val pending = pendingChanges.toList
          pendingChanges.clear()
          reply(pending)
        case m => log.error("ActiveSubscriptions: unexpected actor message: %s", m)
      }
    }
  }
}

/** 
 * LATER multiple watches on the same objects will queue duplicate changes unnecessarily.  Fix that.
 * 
 * CONSIDER breaking this into two classes, one as a trait mixed into AppContext, the other
 * with an actor

 */