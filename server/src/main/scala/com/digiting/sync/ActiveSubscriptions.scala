package com.digiting.sync
import actors.Actor._
import actors.Actor
import collection._
import com.digiting.sync.syncable.Subscription
import net.liftweb.util.Log

/**
 * Maintains a set of observations on the syncable object trees referenced by a given
 * connection's subscriptions.  The changes are queued.  Clients retrieve changes via takePending().
 * 
 * SOON synchronize subscriptions array with client #subscriptions object instead
 */
class ActiveSubscriptions(connection:Connection) extends Actor {
  val subscriptions = new mutable.HashSet[Syncable] 		 // active subscriptions
  val pendingChanges = new mutable.Queue[ChangeDescription]  // model changes waiting for a commit.  

  start
  
  /** watch the set of objects referenced in the current subscription */
  def subscribe(sub:Subscription) {
    Partitions get sub.inPartition match {
      case Some(partition) =>
        partition.published find sub.name match {
	      case Some(root) => 
	        subscriptions + root
	        Observers.watchDeep(sub, queueChanges, "ActiveSubscriptions")
	        Observers.currentMutator.withValue("ActiveSubscriptions") {
	          sub.root = root;	// update will propogate to client
	        }
	      case _ =>
	        Log.error("unknown subscription name: " + sub.name +" in subscription: " + sub)          
        }
      case None => Log.error("ActiveSubscriptions.subscribe() partition not found: " + sub.partition)
    }
  }
  
  /** remember a change that we'll later send to the client */
  private def queueChanges(change:ChangeDescription):Unit = {      
    if (change.source != connection.connectionId) {
      this ! change
//      Console println "ActiveSubscriptions(" + connection.connectionId + ") change queued: " + change
    } else {
//      Console println "ActiveSubscriptions(" + connection.connectionId + ") not queueing change: originated from client: " + change
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
        case m => Log.error("ActiveSubscriptions: unexpected actor message: " + m)
      }
    }
  }
}
