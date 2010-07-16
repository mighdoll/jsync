package com.digiting.sync
import Observers.DataChangeFn
import com.digiting.util.MultiMap // LATER replace this with SCALA 2.8

trait HasWatches {
	
  val watchers = new MultiMap[Syncable, DataChangeFn]
  def watch(syncable:Syncable)(dataChangeFn:DataChangeFn) { 
    watchers += (syncable, dataChangeFn)
  }
    
}
