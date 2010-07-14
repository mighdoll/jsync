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
import com.digiting.util._
import java.io.Serializable
import scala.collection.{mutable,immutable}
import Log2._

object Pickled {
  implicit private val log = logger("Pickled")
  def apply[T <: Syncable](reference:SyncableReference, version:String,
      properties:Map[String, SyncableValue]) =
    new Pickled(reference, version, properties, Set.empty)
  
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
      a = trace2("pickling %s: %s %s", syncable.fullId, prop, value)
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

case class ClientId (val id:String)
case class RequestId (val id:String)
case class PickledWatch (val clientId:ClientId, val requestId:RequestId, val expiration:Long)

@serializable
class Pickled(val reference:SyncableReference, val version:String,
    val properties:Map[String, SyncableValue], val watches:Set[PickledWatch])  {
  implicit private val log = logger("Pickled")
  
  def unpickle():Syncable = {
    val syncable:Syncable = newBlankSyncable(reference.kind, reference)
    syncable.version = version
    val classAccessor = SyncableAccessor.get(syncable.getClass)
    Observers.withNoNotice {  
      App.app.withNoVersioning {
        for ((propName, value) <- properties) {
          classAccessor.set(syncable, propName, unpickleValue(value.value))
        }
      }
    }

    trace2("unpickled %s", syncable)
    syncable
  }
  
  private def boxValue(value:Any):AnyRef = {
    value match {                 // this triggers boxing converion
      case ref:AnyRef => ref      // matches almost everything
      case null => null
      case x => 
        err2("boxValue unexpected code path for: " + x)
        throw new ImplementationError
    }
  }
  
  private def unpickleValue(value:Any):AnyRef = {
    value match {
      case ref:SyncableReference =>
        App.app.get(ref) getOrElse {
          err2("unpickleValue can't find referenced object: ", ref)
          throw new ImplementationError
        }          
      case other => boxValue(other)
    }
  }
  
  /** return a new Pickled with the change applied */
  def +(propChange:PropertyChange):Pickled = {
    trace2("revise() %s", propChange)
    if (propChange.versions.old != version) {
      warning2("update() versions don't match on changed.old=%s actual(current)=%s  %s  %s", 
        propChange.versions.old, version, propChange, this)
    }
    val updatedProperties = properties + (propChange.property -> propChange.newValue)
    new Pickled(reference, propChange.versions.now, updatedProperties, watches)
  }
  
  def +(watch:PickledWatch) = {
    val moreWatches = watches + watch
    val pickled = new Pickled(reference, version, properties, moreWatches)    
    trace2("+watch() %s", pickled)    
    pickled
  }
  
  def -(watch:PickledWatch):Pickled = {
    val lessWatches = watches - watch
    val pickled = new Pickled(reference, version, properties, lessWatches)    
    trace2("-watch() %s", pickled)
    pickled
  }
  
  override def toString:String = {
    val props = properties map Function.tupled {(k,v) => k + "=" + v.toString}
    
    String.format("<%s %s %s>", reference, version, props)
  }
  
}
