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
    val pickledWatchFn = watchFns get id.partitionId getOrElse 
      makePartitionWatchFn(id.partitionId)
    
    // queue a BeginObserve change to the partition
    val partitionWatch = new BeginObserveChange(id, pickledWatchFn)
    App.app.instanceCache.changeNoticed(partitionWatch)
  }
  
  /** make a new pickled watch function for a given partition */
  private def makePartitionWatchFn(partitionId:PartitionId):PickledWatch = {
	  val partition = Partitions(partitionId)
    val pickled = partition.pickledWatchFn(
      {this ! (partition,_)}, partitionWatchTimeout)
    watchFns(partitionId) = pickled
    pickled
  }

  /** called when we receive a transactions worth of changes from the partition */
  def applyPartitionChanges(partition:Partition, changes:Seq[DataChange]) {
    withApp {
      changes.first.target.partitionId
    
      changes foreach {trace2("#%s Change received from partition %s", debugId, _)}
  
      changes flatMap {_.references} foreach {get(_)}

      Observers.pauseNotification {
        changes foreach {modify(_)}
        
        // hack the changes out of the instnace cache lest they go back to the partition
        Observers.releasePaused {_ == instanceCache}  
        val toss = instanceCache.drainChanges()         
        trace2({"partitionChanged() tossing partition changes: " +
                (toss mkString("\n\ttoss: ", "\n\ttoss: ", ""))})
        
      } 
      notifyWatchers() // app notification released here, possibly generating more changes
    } // client and any partition notification (of app changes subsequent to toss above) sent here, in withApp.commit()
    
    /* SOON - consider revising this.  apps should queue notifications (as the instanceCache
     * and subscriptions do), rather than relaying watches immediately.  Then we can stop this
     * pauseNotification silliness and just manipulate the queues directly.  
     */
  }
  
  /** apply one modification */
  private def modify(change:DataChange) {
    remoteChange.withValue(change) {
      change match {
        case created:CreatedChange => NYI()
        case property:PropertyChange => 
          withGetId(property.target) {obj =>
          	trace2("#%s applying property: %s", debugId, change)
            SyncableAccessor(obj).set(obj, property.property, getValue(property.newValue))          
          }
        case collectionChange:CollectionChange => 
          withGetId(collectionChange.target) {obj =>
            trace2("#%s collectionChange : %s", debugId, change)
            obj match {
              case collection:SyncableCollection => collection.revise(collectionChange)
            }
          }
        case deleted:DeletedChange => NYI()        
      }
    }
  }
  
  private def getValue(value:SyncableValue):AnyRef = 
    value.value match {
      case ref:SyncableReference => get(ref) get
      case v => v.asInstanceOf[AnyRef]
    }

    // SOON Add timeout re-registration 
}
