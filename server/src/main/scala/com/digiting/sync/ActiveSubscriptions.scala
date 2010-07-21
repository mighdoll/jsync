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
import com.digiting.util._
import Log2._

/**
 * Maintains a set of observations on the syncable object trees referenced by a given
 * connection's subscriptions.  
 * 
 * The changes to subscribed objects are queued.  Clients of this class can retrieve 
 * the queued changes via takePending().
 */
class ActiveSubscriptions(app:AppContext) extends Actor {
  implicit private val log = logger("ActiveSubscriptions")
  
  val subscriptions = new mutable.HashSet[Syncable] 		 // active subscription roots
  val deepWatches = new mutable.HashSet[DeepWatch] 		 	 // active subscription root deep watches
  val pendingChanges = new mutable.Queue[ChangeDescription]  // model changes waiting for a commit.  
  def debugId = app.debugId
  
  start
  
  trace2("#%s created", app.debugId)
  
  /** watch the set of objects referenced in the subscription request.  Modify the client's 
   * subscription request object by seeing its root field.  The change to the request object
   * propogates back to the client */
  def subscribe(sub:Subscription) {
    Partitions get sub.inPartition match {
      case Some(partition) =>
        partition.published find sub.name match {
          case Some(root) => 
            trace2("#%s subscribe to: %s,  root: %s", app.debugId, sub.name, root)
            subscriptions += root
            subscribeRoot(sub)
            Observers.currentMutator.withValue("ActiveSubscriptions") {
              sub.root = root;	// update will propogate to client
            }
          case _ =>
            error2("can't find subscription name: " + sub.name + ", subscription: " + sub)          
        }
      case None => error2("subscribe: partition not found: " + sub.inPartition)
    }
  }

  def unsubscribe(sub:Subscription) {
    subscriptions -= sub
    unsubscribeRoot(sub)
  }
  
  def unsubscribeRoot(root:Syncable) {
    deepWatches find {deep:DeepWatch => deep.root == root} map {deep =>
      trace2("unsubscribe deepwatch: %s", deep)
      deepWatches -= deep
      deep disable
    } orElse {
      err2("deepWatch not found for root: " + root)
    }    
  }
  
  /** watch the given root object */
  def subscribeRoot(root:Syncable) {
    val deep = Observers.watchDeep(root, treeChanged, treeChanged, this)
    deepWatches += deep
    trace2("#%s, subscribeRoot() subscribed %s deepwatch: %s", app.debugId, root, deep)
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
  
 
  /** This routine is called when an object in a subscribed tree is changed
   * and when a new object is added to the set of objects referenced in the subscribed tree.  
   * 
   * Based on what was changed and who changed it, this routines queues changes destined 
   * to be sent to the client, and sets watches on the underlying partition.
   */
  private def treeChanged(change:ChangeDescription):Unit = {      
    change match {
      case begin:BeginWatch =>  
        // client cares about this object, so watch it in the backing store too
        app.observeInPartition(begin.observee)  
        
        // We send the client all new objects added to the subscription tree
        // except the root subscription object (which came from the client)
        if (change.source != app.connection.connectionId) {
          App.app.get(begin.observee) match {
            case Some(sub:Subscription) => 
            case Some(obj) => 
              addedToSubscription(obj, begin.watcher)
            case x =>
              abort2("#%s treeChanged() observee not found: %s", begin)
          }
        } else {
          trace2("#%s not queueing BeginWatch change: originated from client: %s", debugId, change)
        }
        
      case _ if change.source == app.connection.connectionId =>
        trace2("#%s not queueing change: originated from client: %s", debugId, change)
        
      case _ =>  
        queueChange(change)
      }
  }
  
  private def queueChange(change:ChangeDescription) {
    trace2("#%s change queued: %s", app.debugId, change)
    this ! change
  }
  
  /** called when a new object has been added to one of the client subscriptions */
  private def addedToSubscription(obj:Syncable, watcher:DeepWatch) {
    queueChange(ResyncChange(Pickled(obj)))
    
    obj match {
      case collection:SyncableCollection =>
        val elements = collection.syncableElementIds
        if (!elements.isEmpty)
          queueChange(BaseMembership(collection.id, elements, watcher))  
      case _ =>                                           
    }
    // LATER consider optimizing this so that the we don't send queue changes twice
    // if an object is modified in two subscriptions..
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