package com.digiting.sync
import Partition._

/* Concrete Partition subclasses should implement these functions */
trait ConcretePartition {
  private[sync] def modify(change:StorableChange, tx:Transaction):Unit 
  private[sync] def get(id:InstanceId, tx:Transaction):Option[Pickled]
  private[sync] def commit(tx:Transaction):Boolean 
    
  /** get a registered observation */
  private[sync] def getWatches(id:InstanceId, tx:Transaction):Set[PickledWatch]

  /** debug utility, prints contents to log */
  def debugPrint() {}

  /** erase all of the data in the partition, including the PublishedRoots */
  def deleteContents():Unit
  
  /** true if this partition actually stores objects (false for .transient) */
  def hasStorage = true
}

/** this is a trick to allow a simulated client to refer to a Partition instance 
  * with the name ".transient".  
  * 
  * LATER consider whether we still need a stub partition interface (perhaps just 
  * an id is enough, e.g. TransientPartitionId)
  */
object TransientPartition extends FakePartition(".transient")

class FakePartition(partitionId:String) extends Partition(partitionId) {
  def get(instanceId:InstanceId, tx:Transaction):Option[Pickled] = None
  def modify(change:StorableChange, tx:Transaction):Unit  = {}
  def commit(tx:Transaction) = true
  private[sync] def watch(id:InstanceId, watch:PickledWatch, tx:Transaction) {}
  private[sync] def unwatch(id:InstanceId, watch:PickledWatch, tx:Transaction) {}
  private[sync] def getWatches(id:InstanceId, tx:Transaction):Set[PickledWatch] = Set.empty
  override def hasStorage = false
  def deleteContents() {}
  
}
