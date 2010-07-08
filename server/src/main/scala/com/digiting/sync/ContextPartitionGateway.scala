package com.digiting.sync
import com.digiting.util._
import collection.mutable
import Log2._

trait ContextPartitionGateway  {
  self:AppContext =>
  
  private implicit lazy val logg = logger("ContextPartitionGateway")
  private val partitionWatchTimeout = 100000
  
  // one watch function for each partition containing an object we're watching  
  val watchFns = new mutable.HashMap[PartitionId,PickledWatch]

  /**  Watch for changes made by others to an object in the partition store.
   */  
  def observeInPartition(id:SyncableId) {
    val pickledWatchFn = watchFns get id.partitionId getOrElse 
      makePartitionWatchFn(id.partitionId)
    
    val partitionWatch = new ObserveChange(id, pickledWatchFn)
    App.app.instanceCache.changeNoticed(partitionWatch)
  }
  
  /** make a new pickled watch function for a given partition */
  private def makePartitionWatchFn(partitionId:PartitionId):PickledWatch = {
	  val partition = Partitions(partitionId)
    val pickled = partition.pickledWatchFn(
      {partitionChange(partition,_)}, partitionWatchTimeout)
    watchFns(partitionId) = pickled
    pickled
  }

  /** called when we receive a change from the partition */
  private def partitionChange(partition:Partition, changes:Seq[DataChange]) {
    changes.first.target.partitionId
  
    changes foreach {trace2("#%s Change received from partition %s", debugId, _)}

    changes flatMap {_.references} foreach {get(_)}
    Observers.withMutator(partition.partitionId) {
      for {
        change <- changes
      } {
        modify(change)
      }
    }
  }
  
  private def modify(change:DataChange) {
    change match {
      case created:CreatedChange => NYI()
      case property:PropertyChange => 
        withGetId(property.target) {obj =>
        	trace2("#%s applying property: %s", debugId, change)
          SyncableAccessor(obj).set(obj, property.property, getValue(property.newValue))          
        }
      case deleted:DeletedChange => NYI()
      case insertAt:InsertAtChange => NYI()
      case removeAt:RemoveAtChange => NYI()
      
      case move:MoveChange => NYI()
      case putMap:PutMapChange => NYI()
      case removeMap:RemoveMapChange => NYI()
      case put:PutChange => NYI()
      case remove:RemoveChange => NYI()
      case clear:ClearChange => NYI()
    }
  }
  
  private def getValue(value:SyncableValue):AnyRef = 
    value.value match {
      case ref:SyncableReference => get(ref.id) get
      case v => v.asInstanceOf[AnyRef]
    }

    // TODO Add timeout re-registration 
}
