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
import SyncManager.newSyncable
import com.digiting.util._
import java.io.Serializable
import scala.collection.{mutable,immutable}
import Log2._

object Pickled {
  implicit private val log = logger("Pickled")
  def apply[T <: Syncable](id:KindVersionedId, instanceVersion:String,
      properties:Map[String, SyncableValue]) =
    new Pickled(id, instanceVersion, properties, Set.empty)
  
  /**
   * Pickle the provided syncable
   * 
   * Collections to be pickled should be empty.  (DataChange event streams 
   * contain add/put operations to fill the collection from its empty state.)  
   */
  def apply[T <: Syncable](syncable:T):Pickled = {
    val ref = KindVersionedId(syncable)
    val props = new HashMap[String, SyncableValue]
    for {
      (prop, value) <- SyncableAccessor.properties(syncable) if !isReserved(prop)
      a = trace2("pickling property in %s.%s=%s", syncable, prop, value)
      syncValue = SyncableValue.convert(value)
    } {
      props + (prop -> syncValue)
    }
    val pickledObj = Pickled(ref, syncable.version, Map.empty ++ props)
    syncable match {
      case collection:SyncableCollection => 
        collection match {
          case seq:SyncableSeq[_]=>
            PickledSeq(pickledObj, PickledSeq.emptyMembers)
          case set:SyncableSet[_] =>
            PickledSet(pickledObj, PickledSet.emptyMembers)
          case map:SyncableMap[_,_] =>
            PickledMap(pickledObj, PickledMap.emptyMembers)
        }
      case _ => pickledObj
    }    
  }  
}

case class RequestId (val id:String)
case class PickledWatch (val watcherId:SyncNode, val requestId:RequestId, val expiration:Long)

@serializable
class Pickled(val id:KindVersionedId, val instanceVersion:String,
    val properties:Map[String, SyncableValue], val watches:Set[PickledWatch])  {
  implicit private val log = logger("Pickled")
  
  def unpickle():Syncable = {
    val app = App.app
    val syncable = app.enableChanges(false) { newSyncable(id) }
    syncable.version = instanceVersion
    
    val classAccessor = SyncableAccessor.get(syncable.getClass)    
    for ((propName, pickledValue) <- properties) {      
      val value = unpickleValue(pickledValue.value) 
      app.enableChanges(false) {
        classAccessor.set(syncable, propName, value) 
      }
    }
  	
    val latest = migrateToLatest(syncable)
    trace2("unpickled %s", latest)
    latest
  }
  
  private def migrateToLatest(syncable:Syncable):Syncable = {
    syncable match {
      case migrator:MigrateTo[_] => 
        App.app.enableChanges(true) { migrator.migrate }
      case normal => normal
    }
  }
  
  private def boxValue(value:Any):AnyRef = {
    value match {                 // this triggers boxing converion
      case ref:AnyRef => ref      // matches almost everything
      case null => null
      case x => 
        abort2("boxValue unexpected code path for: " + x)
    }
  }
  
  private def unpickleValue(value:Any):AnyRef = {
    value match {
      case ref:SyncableReference =>
        App.app.get(ref) getOrElse {
          abort2("unpickleValue can't find referenced object: ", ref)
        }          
      case other => boxValue(other)
    }
  }
  
  /** return a new Pickled with the change applied */
  def +(propChange:PropertyChange):Pickled = {
    trace2("revise() %s", propChange)
    if (propChange.versions.old != instanceVersion) {
      abort2("update() versions don't match on changed.old=%s actual(current)=%s  %s  %s", 
        propChange.versions.old, instanceVersion, propChange, this)
      // SOON notify mutator of conflict
    }
    val updatedProperties = properties + (propChange.property -> propChange.newValue)
    new Pickled(id, propChange.versions.now, updatedProperties, watches)
  }
  
  def +(watch:PickledWatch) = {
    val moreWatches = watches + watch
    val pickled = new Pickled(id, instanceVersion, properties, moreWatches)    
    trace2("+watch() %s", pickled)    
    pickled
  }
  
  def -(watch:PickledWatch):Pickled = {
    val lessWatches = watches - watch
    val pickled = new Pickled(id, instanceVersion, properties, lessWatches)    
    trace2("-watch() %s", pickled)
    pickled
  }
  
  override def toString:String = {
    import Function.tupled
    val props = properties map tupled {(k,v) => k + "=" + v.toString} mkString("(", ",", ")")
    
    String.format("<%s %s %s>", id, instanceVersion, props)
  }
  
}
