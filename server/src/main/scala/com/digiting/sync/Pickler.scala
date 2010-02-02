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
    
    syncable
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
}
