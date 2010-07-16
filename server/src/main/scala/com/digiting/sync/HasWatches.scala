package com.digiting.sync
import Observers.DataChangeFn
import collection.mutable

trait HasWatches {
  case class Notification(fn:DataChangeFn, change:DataChange)
	def pending = new mutable.Queue[Notification]
 
  /** watch a syncable: changes will be reported to the app at commit time */
  def watch(syncable:Syncable)(dataChangeFn:DataChangeFn) { 
    Observers.watch(syncable, this, {change =>
      pending += Notification(dataChangeFn, change)
    })
  }
    
}
