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

/**
 * Maintains a set of observations on the syncable object trees referenced by a given
 * connection's subscriptions.  
 * 
 * The changes to subscribed objects are queued.  Clients of this class can retrieve 
 * the queued changes via takePending().
 */
class ActiveSubscriptions(connection:Connection) extends Actor {
  val log = Logger("ActiveSubscriptions")
  val subscriptions = new mutable.HashSet[Syncable] 		 // active subscription roots
  val deepWatches = new mutable.HashSet[DeepWatch] 		 	 // active subscription root deep watches
  val pendingChanges = new mutable.Queue[ChangeDescription]  // model changes waiting for a commit.  
  val serverOnlyMutator = "ServerOnly-ActiveSubscriptions"
  
  start
  
  log.trace("#%s created", connection.debugId)
  
  /** watch the set of objects referenced in the subscription request.  Modify the client's 
   * subscription request object by seeing its root field.  The change to the request object
   * propogates back to the client */
  def subscribe(sub:Subscription) {
    Partitions get sub.inPartition match {
      case Some(partition) =>
        partition.published find sub.name match {
          case Some(root) => 
            log.trace("#%s subscribe to: %s,  root: %s", connection.debugId, sub.name, root)
            subscriptions += root
            deepWatches += 
              Observers.currentMutator.withValue(serverOnlyMutator) {
                Observers.watchDeep(sub, queueChanges, "ActiveSubscriptions")
              }
            Observers.currentMutator.withValue("ActiveSubscriptions") {
              sub.root = root;	// update will propogate to client
            }
          case _ =>
            log.error("can't find subscription name: " + sub.name + ", subscription: " + sub)          
        }
      case None => log.error("subscribe: partition not found: " + sub.inPartition)
    }
  }
  
  /** remember a change that we'll later send to the client */
  private def queueChanges(change:ChangeDescription):Unit = {      
    change match {
      case _ if change.source == connection.connectionId =>
        log.trace("ActiveSubscriptions #%s not queueing change: originated from client: %s", 
    	  connection.debugId, change)
      case WatchChange(_,_,watcher:DeepWatch) if deepWatches.contains(watcher) =>
        queueChange(change)
      case WatchChange(_,_,_) =>
        log.trace("ActiveSubscriptions #%s not queueing watch change from another deepwatch: %s", 
    	  connection.debugId, change)
      case _ if change.source == serverOnlyMutator =>
        log.warning("ActiveSubscriptions #%s unexpected serverOnly mutator,  shouldn't the deepwatch check catch this?  change: %s", 
    	  connection.debugId, change)
      case _ =>  
        queueChange(change)
      }
  }
  
  private def queueChange(change:ChangeDescription) {
    log.trace("ActiveSubscriptions #%s change queued: %s", connection.debugId, change)
    this ! change
    queueCollectionEdits(change)
  }
  
  // if a collection is was added to the active set of the subscription,
  // queue edits to the collection to add the elements (the elements themselves will get 
  // own Watch changes sent from DeepWatch)  
  private def queueCollectionEdits(change:ChangeDescription) {
    change match {
      case watchChange:WatchChange =>
        watchChange.newValue match {
          case set:SyncableSet[_] =>
            log.trace("queueCollectionEdits:  %d edits on %s", set.size, set)
            this ! BaseMembership(set, set.syncableElements.toList)
          case seq:SyncableSeq[_] =>
            log.trace("queueCollectionEdits:  %d edits on %s", seq.length, seq)
            this ! BaseMembership(seq, seq.syncableElements.toList)
          case map:SyncableMap[_,_] =>
            throw new NotYetImplemented
          case _ =>
        }
      case _ =>          
    }    
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

  case class TakePending()
  
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
