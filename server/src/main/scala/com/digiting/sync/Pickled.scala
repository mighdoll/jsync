/*   Copyright [2010] Digiting Inc
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.digiting.sync
import scala.collection.mutable.HashMap
import SyncableInfo.isReserved
import SyncManager.newBlankSyncable
import net.lag.logging.Logger
import com.digiting.util.LogHelper
import java.io.Serializable
import scala.collection.mutable.ArrayBuffer
import scala.collection.{mutable,immutable}


object Pickled {
  val log = Logger("Pickled")
  def apply[T <: Syncable](reference:SyncableReference, version:String,
      properties:Map[String, SyncableValue]) =
    new Pickled(reference, version, properties)
  
  /**
   * Pickle the provided syncable
   * 
   * Collections to be pickled should be empty.  (DataChange event streams 
   * contain add/put operations to fill the collection from its empty state.)  
   */
  def apply[T <: Syncable](syncable:T):Pickled = {
    val ref = SyncableReference(syncable)
    val props = new HashMap[String, SyncableValue]
    for {
      (prop, value) <- SyncableAccessor.properties(syncable) if !isReserved(prop)
      a = log.trace("pickling %s: %s %s", syncable.fullId, prop, value)
      syncValue = SyncableValue.convert(value)
    } {
      props + (prop -> syncValue)
    }
    syncable match {
      case collection:SyncableCollection => 
        val members = 
          collection match {
            case seq:SyncableSeq[_]=>
              assert (seq.list == null || seq.length == 0)
              new immutable.Queue[SyncableReference] with PickledSeqMembers 
            case set:SyncableSet[_] =>
              assert (set.set == null || set.size == 0)
              new immutable.HashSet[SyncableReference] with PickledSetMembers
            case map:SyncableMap[_,_] =>
              assert (map.size == 0)
              new immutable.HashMap[Serializable, SyncableReference] with PickledMapMembers
          }
        new PickledCollection(ref, syncable.version, Map.empty ++ props, members)
      case _ =>
        Pickled(ref, syncable.version, Map.empty ++ props)
    }
    
  }
}

sealed trait PickledMembers
trait PickledSeqMembers extends PickledMembers with Seq[SyncableReference] 
trait PickledSetMembers extends PickledMembers with Set[SyncableReference]
trait PickledMapMembers extends PickledMembers with Map[Serializable,SyncableReference]

object PickledCollection {
  def apply(p:Pickled, members:PickledMembers) = {
    new PickledCollection(p.reference, p.version, p.properties, members)
  }
}
  
class PickledCollection(ref:SyncableReference, 
    ver:String, props:Map[String,SyncableValue], val members:PickledMembers) 
    extends Pickled(ref,ver,props) {
  protected val slog = Logger("PickledCollection")
  
  override def unpickle[T <: Syncable]:T = {
    val collection:T = super.unpickle
    val partition = Partitions.getMust(collection.fullId.partitionId)
    val instanceId = collection.fullId.instanceId
    Observers.withNoNotice {
      collection match {
        case seq:SyncableSeq[_] =>
          val castSeq = seq.asInstanceOf[SyncableSeq[Syncable]]
          loadRefs(seq, partition.getSeqMembers(instanceId)) foreach {             
            castSeq += _
          }
        case set:SyncableSet[_] =>  // DRY with seq 
          val castSet = set.asInstanceOf[SyncableSet[Syncable]]
          loadRefs(set, partition.getSetMembers(instanceId)) foreach {             
            castSet += _
          }
        case map:SyncableMap[_,_] =>
          val castMap = map.asInstanceOf[SyncableMap[Serializable, Syncable]]
          loadMapRefs(map, partition.getMapMembers(instanceId)) foreach {
            case (key, value) => castMap(key) = value
          }
        case x =>
          throw new ImplementationError("unexpected pickled type: " + x)
      }
    }
    collection
  }
  
  
  /** SOON parallel or batch load multiple objects from the backend for speedier loading from e.g. simpledb */
  private def loadRefs(collection:SyncableCollection, 
      refsOpt:Option[Iterable[SyncableReference]]):Iterable[Syncable] = {    
    for {
      ref <- ensureRefs(collection, refsOpt)
      syncable <- SyncManager.get(ref.id) orElse
        err("loadRefs can't find target: %s in collection %s", ref, collection.fullId)
    } yield 
      syncable
  }
  
  private def ensureRefs[T](collection:SyncableCollection, 
      refsOpt:Option[Iterable[T]]):Iterable[T] = {
    refsOpt match {
      case Some(refs) => 
        refs
      case None => 
        log.error("no member map found for collection: %s", collection.fullId)
        Nil
    }
  }
  
  private def loadMapRefs(collection:SyncableCollection, 
      refsOpt:Option[Map[Serializable,SyncableReference]]):Iterable[(Serializable,Syncable)] = {
    for {
      (key, ref) <- ensureRefs(collection, refsOpt)
      syncable <- SyncManager.get(ref) orElse 
        err("loadMapRefs can't find target: %s in collection %s", ref, collection.fullId)
    } yield {
      (key, syncable)
    }
  }

}

// CONSIDER SCALA type parameters are a hassle for pickling/unpickling.  manifest?  
class Pickled(val reference:SyncableReference, val version:String,
    val properties:Map[String,SyncableValue]) 
    extends LogHelper {
  protected val log = Logger("Pickled")
  
  def unpickle[T <: Syncable]:T = {
    val syncable:T = newBlankSyncable(reference.kind, reference.id) 
    val classAccessor = SyncableAccessor.get(syncable.getClass)
    Observers.withNoNotice {
      for ((propName, value) <- properties) {
        classAccessor.set(syncable, propName, unpickleValue(value.value))
      }
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
  def update(propChange:PropertyChange):Pickled = {
    if (propChange.versions.old != version) {
      log.warning("update() versions don't match.  changed.old=%s current=%s", 
        propChange.versions.old, version)
    }
    val updatedProperties = properties + (propChange.property -> propChange.newValue)
    new Pickled(reference, propChange.versions.current, updatedProperties)
  }
  
}
