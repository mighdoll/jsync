package com.digiting.sync
import Observers.DataChangeFn
import collection.mutable
import com.digiting.util.Log2._

trait HasWatches {
  implicit private val log = logger("HasWatches")
	val toNotify = new mutable.Queue[Notification]

  case class Notification(fn:DataChangeFn, debugName:String, change:DataChange)

  /** watch a syncable: changes will be reported to the app at commit time */
  def watch2(syncable:Syncable, debugName:String)(dataChangeFn:DataChangeFn) { 
    Observers.watch(syncable, this, {change =>
      trace2("watch %s sees change: %s", debugName, change)
      toNotify += Notification(dataChangeFn, debugName, change)
    })
  }
  
  /** watch a syncable: changes will be reported to the app at commit time */
  def watch2(syncable:Syncable)(dataChangeFn:DataChangeFn) { 
    watch2(syncable, "")(dataChangeFn)
  }

  /** notify queued watches */
  def notifyWatchers() {
    var repeats = 0
    while (!toNotify.isEmpty) {
      val copy = toNotify.clone
      toNotify.clear    
      copy foreach {notification =>
      	trace2("notifyWatchers notifying %s of change: %s", notification.fn, notification.change)
        notification.fn(notification.change)
      }
      repeats = repeats + 1
      if (repeats > 20) {
        abort2("app notification called 21 times, blackjack!  (looks like a recursive watch+modify)")
      }
    } // repeat in case the notified functions make more changes
  }
    
}
