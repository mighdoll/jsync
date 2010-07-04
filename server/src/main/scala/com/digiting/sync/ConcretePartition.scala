package com.digiting.sync
import Partition._

/* Concrete Partition subclasses should implement these functions */
trait ConcretePartition {
  private[sync] def modify(change:DataChange, tx:Transaction):Unit 
  private[sync] def get(id:InstanceId, tx:Transaction):Option[Pickled]
  private[sync] def commit(tx:Transaction):Boolean 
  
  /** register for observation of an object */
  private[sync] def watch(id:InstanceId, watch:PickledWatch, tx:Transaction):Unit  
  
  /** remove an observation */
  private[sync] def unwatch(id:InstanceId, watch:PickledWatch, tx:Transaction):Unit    
  
  /** get a registered observation */
  private[sync] def getWatches(id:InstanceId, tx:Transaction):Set[PickledWatch]
  
  /** call the watching client */
  private[sync] def notify(watch:PickledWatch, change:DataChange, tx:Transaction) {}

  /** debug utility, prints contents to log */
  def debugPrint() {}

  /** erase all of the data in the partition, including the PublishedRoots */
  def deleteContents():Unit
  
  /** true if this partition actually stores objects (false for .transient) */
  def hasStorage = true
}

/** this is a trick to allow a simulated client to refer to a Partition instance 
  * with the name ".transient".  LATER we should change the SyncableIdentity to use a string
  * for the partition id, rather than the partition reference (...SyncableIdentity is gone now.)
  */
object TransientPartition extends FakePartition(".transient")

class FakePartition(partitionId:String) extends Partition(partitionId) {
  def get(instanceId:InstanceId, tx:Transaction):Option[Pickled] = None
  def modify(change:DataChange, tx:Transaction):Unit  = {}
  def commit(tx:Transaction) = true
  private[sync] def watch(id:InstanceId, watch:PickledWatch, tx:Transaction) {}
  private[sync] def unwatch(id:InstanceId, watch:PickledWatch, tx:Transaction) {}
  private[sync] def getWatches(id:InstanceId, tx:Transaction):Set[PickledWatch] = Set.empty
  override def hasStorage = false
  def deleteContents() {}
}
