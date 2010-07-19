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
    
    queueWatch(id, pickledWatchFn)    
  }
  
  /** make a new pickled watch function for a given partition */
  private def makePartitionWatchFn(partitionId:PartitionId):PickledWatch = {
    val partition = Partitions(partitionId)
    val pickled = partition.pickleWatch(partitionWatchTimeout) {changes =>
      this ! (partitionId, changes)
    }
    watchFns(partitionId) = pickled
    pickled
  }
  
  private def queueWatch(id:SyncableId, pickledWatch:PickledWatch) {
    // queue a BeginObserve change to be sent to the partition
    instanceCache.changeNoticed(BeginObserveChange(id, pickledWatch))    
  } 
  
  import RamWatches.PartitionWatchFn
  def customObservePartition(id:SyncableId)(changeFn: PartitionWatchFn) {
    customObservePartition(id, partitionWatchTimeout)(changeFn)
  }
  
  def customObservePartition(id:SyncableId, timeout:Int)(changeFn: PartitionWatchFn) {
    val partition = Partitions(id.partitionId)
    val pickledWatch = partition.pickleWatch(timeout)(changeFn)
    
    queueWatch(id, pickledWatch)
  }

    // SOON Add timeout re-registration 
}
