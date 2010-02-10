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
    val pickledObj = Pickled(ref, syncable.version, Map.empty ++ props)
    syncable match {
      case collection:SyncableCollection => 
        collection match {
          case seq:SyncableSeq[_]=>
            assert (seq.list == null || seq.length == 0)
            PickledSeq(pickledObj, PickledSeq.emptyMembers)
          case set:SyncableSet[_] =>
            assert (set.set == null || set.size == 0)
            PickledSet(pickledObj, PickledSet.emptyMembers)
          case map:SyncableMap[_,_] =>
            assert (map.size == 0)
            PickledMap(pickledObj, PickledMap.emptyMembers)
        }
      case _ => pickledObj
    }    
  }
  
}


object LoadRefs extends LogHelper {
  val log = Logger("LoadRefs")  
  
    /** SOON parallel or batch load multiple objects from the backend for speedier loading from e.g. simpledb */
  def loadRefs(collection:SyncableCollection, 
      refs:Iterable[SyncableReference]):Iterable[Syncable] = {    
    for {
      ref <- refs
      syncable <- SyncManager.get(ref.id) orElse
        err("loadRefs can't find target: %s in collection %s", ref, collection.fullId)
    } yield 
      syncable
  }
  
  def loadMapRefs(collection:SyncableCollection, 
      refs:Map[Serializable,SyncableReference]):Iterable[(Serializable,Syncable)] = {
    for {
      (key, ref) <- refs
      syncable <- SyncManager.get(ref) orElse 
        err("loadMapRefs can't find target: %s in collection %s", ref, collection.fullId)
    } yield {
      (key, syncable)
    }
  }        
}
import LoadRefs._

trait PickledCollection

object PickledSeq {
  def apply(p:Pickled, members:mutable.Buffer[SyncableReference] ) = {
    new PickledSeq(p.reference, p.version, p.properties, members)
  }
  val emptyMembers = new ArrayBuffer[SyncableReference] 
}

object PickledSet {
  def apply(p:Pickled, members:Set[SyncableReference]) = {
    new PickledSet(p.reference, p.version, p.properties, members)
  }
  val emptyMembers = new immutable.HashSet[SyncableReference]
}

object PickledMap {
  def apply(p:Pickled, members:Map[Serializable, SyncableReference]) = {
    new PickledMap(p.reference, p.version, p.properties, members)
  }
  val emptyMembers = new immutable.HashMap[Serializable, SyncableReference] 
}

@serializable
class PickledSeq(ref:SyncableReference, ver:String, 
    props:Map[String,SyncableValue], val members:mutable.Buffer[SyncableReference]) 
    extends Pickled(ref,ver,props) with PickledCollection {
  override def unpickle:SyncableSeq[Syncable] = {
    val seq = super.unpickle.asInstanceOf[SyncableSeq[Syncable]]
    Observers.withNoNotice {
      loadRefs(seq, members) foreach {             
        seq += _
      }
    }
    seq
  }
}
    
@serializable
class PickledSet(ref:SyncableReference, ver:String, 
    props:Map[String,SyncableValue], val members:Set[SyncableReference]) 
    extends Pickled(ref,ver,props) with PickledCollection{
  override def unpickle:SyncableSet[Syncable] = {
    val set = super.unpickle.asInstanceOf[SyncableSet[Syncable]]
    Observers.withNoNotice {
      loadRefs(set, members) foreach {             
        set += _
      }
    }
    set
  }
}
    
@serializable
class PickledMap(ref:SyncableReference, ver:String, 
    props:Map[String,SyncableValue], val members:Map[Serializable, SyncableReference]) 
    extends Pickled(ref,ver,props) with PickledCollection{
  override def unpickle:SyncableMap[Serializable, Syncable] = {
    val map = super.unpickle.asInstanceOf[SyncableMap[Serializable, Syncable]]
    Observers.withNoNotice {
      loadMapRefs(map, members) foreach {
        case (key, value) => map(key) = value
      }
    }
    map
  }
}
    

@serializable
class Pickled(val reference:SyncableReference, val version:String,
    val properties:Map[String, SyncableValue]) extends LogHelper {
  protected val log = Logger("Pickled")
  
  def unpickle:Syncable = {
    val syncable:Syncable = newBlankSyncable(reference.kind, reference.id) 
    val classAccessor = SyncableAccessor.get(syncable.getClass)
    Observers.withNoNotice {
      for ((propName, value) <- properties) {
        classAccessor.set(syncable, propName, unpickleValue(value.value))
      }
    }
    SyncManager.instanceCache put syncable
    
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
  
  /** return a new Pickled with the change applied */
  def revise(propChange:PropertyChange):Pickled = {
    if (propChange.versions.old != version) {
      log.warning("update() versions don't match.  changed.old=%s current=%s", 
        propChange.versions.old, version)
    }
    val updatedProperties = properties + (propChange.property -> propChange.newValue)
    new Pickled(reference, propChange.versions.current, updatedProperties)
  }
  
}
