package com.digiting.sync
import scala.collection.mutable.HashMap
import SyncableInfo.isReserved
import SyncManager.newBlankSyncable
import net.lag.logging.Logger
import com.digiting.util.LogHelper


object Pickled {
  def apply[T <: Syncable](reference:SyncableReference, version:String,
      properties:Map[String, SyncableValue]) =
    new Pickled[T](reference, version, properties)
  
  def apply[T <: Syncable](syncable:T):Pickled[T] = {
    val ref = SyncableReference(syncable)
    val props = new HashMap[String, SyncableValue]
    for {
      (prop, value) <- SyncableAccessor.properties(syncable) if !isReserved(prop)
      syncValue = SyncableValue.convert(value)
    } {
      props + (prop -> syncValue)
    }
    
    Pickled(ref, syncable.version, Map.empty ++ props)
  } 

}

// CONSIDER SCALA type parameters are a hassle for pickling/unpickling.  manifest?  
class Pickled[T <: Syncable](val reference:SyncableReference, val version:String,
    val properties:Map[String,SyncableValue]) 
  extends LogHelper {
  val log = Logger("Pickled")
  
  def unpickle:T = {
    val syncable:T = newBlankSyncable(reference.kind, reference.id) 
    val classAccessor = SyncableAccessor.get(syncable.getClass)
    Observers.withNoNotice {
      for ((propName, value) <- properties) {
        classAccessor.set(syncable, propName, unpickleValue(value.value))
      }
    }
    syncable match {
      case collection:SyncableCollection =>
        loadMembers(collection)
      case _ =>
    }
    SyncManager.instanceCache put syncable
    
    syncable
  }
  
  /** load the membership list of this collection.  
   * 
   * We can assume the membership list
   * is not loaded (although the member objects themselves may be loaded) because
   * the collection object has just been unpickled.
   */
  private def loadMembers(collection:SyncableCollection) {
    collection match {
      case seq:SyncableSeq[_] =>
        val memberRefsOpt = 
          Partitions.getMust(seq.fullId.partitionId).getSeqMembers(seq.fullId.instanceId)
        Observers.withNoNotice {
          for {
            memberRefs <- memberRefsOpt
            ref <- memberRefs 
            member <- SyncManager.get(ref.id) orElse
              err("loadMembers can't find target: %s", ref)
            castSeq = seq.asInstanceOf[SyncableSeq[Syncable]]
          } {            
            castSeq += member
          }
        }
      case set:SyncableSet[_] =>
      case _ =>  
        throw new NotYetImplemented
    }
    // fetch a list of member references from the partition, and load each member object
  }
  
  private def boxValue(value:Any):AnyRef = {
    value match {                 // this triggers boxing converion
      case ref:AnyRef => ref      // matches almost everything
      case null => null
      case x => 
        err("boxValue unexpected code path for: " + x)
        throw new ImplementationError
    }
  }
  
  private def unpickleValue(value:Any):AnyRef = {
    value match {
      case ref:SyncableReference =>
        SyncManager.get(ref) getOrElse {
          err("unpickleValue can't find referenced object: ", ref)
          throw new ImplementationError
        }          
      case other => boxValue(other)
    }
  }
  
  /** return a new Pickled with the change applied */
  def update(propChange:PropertyChange):Pickled[T] = {
    if (propChange.versions.old != version) {
      log.warning("update() versions don't match.  changed.old=%s current=%s", 
        propChange.versions.old, version)
    }
    val updatedProperties = properties + (propChange.property -> propChange.newValue)
    new Pickled(reference, propChange.versions.current, updatedProperties)
  }
  
}
