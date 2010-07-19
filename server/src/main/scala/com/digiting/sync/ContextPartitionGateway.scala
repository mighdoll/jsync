package com.digiting.sync
import com.digiting.util._
import collection.mutable
import Log2._

trait ContextPartitionGateway  {
  self:AppContext =>
  
  private implicit lazy val logg = logger("ContextPartitionGateway")
  private val partitionWatchTimeout = 100000
  
  // one watch function for each partition containing an object we're watching  
  val watchFns = new mutable.HashMap[PartitionId, PickledWatch]

  /**  Watch for changes made by others to an object in the partition store.
   */  
  def observeInPartition(id:SyncableId) {
    trace2("#%s observeInPartition %s", debugId, id)
    
    val pickledWatchFn = watchFns get id.partitionId getOrElse 
      makePartitionWatchFn(id.partitionId)
    
    // queue a BeginObserve change to the partition
    val partitionWatch = new BeginObserveChange(id, pickledWatchFn)
    App.app.instanceCache.changeNoticed(partitionWatch)
  }
  
  /** make a new pickled watch function for a given partition */
  private def makePartitionWatchFn(partitionId:PartitionId):PickledWatch = {
	  val partition = Partitions(partitionId)
    val pickled = partition.pickleWatch(partitionWatchTimeout) {change=>
      this ! (partitionId, change)
    }
    watchFns(partitionId) = pickled
    pickled
  }

    // SOON Add timeout re-registration 
}
