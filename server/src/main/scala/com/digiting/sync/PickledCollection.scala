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
import com.digiting.util._
import scala.collection.{mutable,immutable}
import java.io.Serializable
import scala.collection.mutable.ArrayBuffer
import LoadReferences._
import Log2._

abstract trait PickledCollection[E] extends Pickled {
  val members:Collection[E]
  def reviseCollection(change:CollectionChange):PickledCollection[E]
  def addWatch(watch:PickledWatch):PickledCollection[E]
  def deleteWatch(watch:PickledWatch):PickledCollection[E]
  
  def revise(change:CollectionChange):PickledCollection[E] = {
    assert(instanceVersion == change.versionChange.old)
    reviseCollection(change)
  }
  
  override def +(watch:PickledWatch):PickledCollection[E] = {
    addWatch(watch)
  }
  
  
  override def +(propChange:PropertyChange):Pickled = NYI()

}

object PickledSeq {
  def apply(p:Pickled, members:mutable.Buffer[SyncableReference] ) = {
    new PickledSeq(p.id, p.instanceVersion, p.properties, Set.empty, members)
  }
  val emptyMembers = new ArrayBuffer[SyncableReference] 
}

object PickledSet {
  def apply(p:Pickled, members:Set[SyncableReference]) = {
    new PickledSet(p.id, p.instanceVersion, p.properties, Set.empty, members)
  }
  val emptyMembers = new immutable.HashSet[SyncableReference]
}

object PickledMap {
  def apply(p:Pickled, members:Map[Serializable, SyncableReference]) = {
    new PickledMap(p.id, p.instanceVersion, p.properties, Set.empty, members)
  }
  val emptyMembers = new immutable.HashMap[Serializable, SyncableReference] 
}

@serializable
class PickledSeq(id:KindVersionedId, ver:String, 
    props:Map[String,SyncableValue], 
    watches:Set[PickledWatch], val members:mutable.Buffer[SyncableReference]) 
    extends Pickled(id,ver,props,watches) with PickledCollection[SyncableReference] {
      
  implicit private val log = logger("pickledSeq")
  
  override def unpickle:SyncableSeq[Syncable] = {
    val seq = super.unpickle.asInstanceOf[SyncableSeq[Syncable]]
    App.app.enableChanges(false){
      loadRefs(seq, members) foreach {             
        seq += _
      }
    }
    seq
  }
  
  def reviseCollection(change:CollectionChange):PickledSeq = {    
    val revisedMembers = members.clone 
    change match {
      case insertAt:InsertAtChange =>
        revisedMembers insert(insertAt.at, insertAt.newVal)
      case removeAt:RemoveAtChange =>
        revisedMembers remove(removeAt.at)
      case move:MoveChange =>
        val moving = revisedMembers remove(move.fromDex)
        revisedMembers insert(move.toDex, moving)
      case clear:ClearChange =>
        revisedMembers.clear
      case _ =>
        throw new ImplementationError
    }
    new PickledSeq(id, change.versionChange.now, properties, watches, revisedMembers)
  }
  
  override def addWatch(watch:PickledWatch):PickledSeq = {
    val moreWatches = watches + watch
    val pickled = new PickledSeq(id, instanceVersion, properties, moreWatches, members)    
    trace2("PickledSeq addWatch() %s", pickled)    
    pickled
  }
  
  override def deleteWatch(watch:PickledWatch):PickledSeq = {
    val moreWatches = watches - watch
    val pickled = new PickledSeq(id, instanceVersion, properties, moreWatches, members)    
    trace2("PickledSeq deletewatch() %s", pickled)    
    pickled
  }
  
}
    
@serializable
class PickledSet(id:KindVersionedId, ver:String, 
    props:Map[String,SyncableValue], watches:Set[PickledWatch], val members:Set[SyncableReference]) 
    extends Pickled(id,ver,props,watches) with PickledCollection[SyncableReference] {
  implicit private val log = logger("PickledSet")
  override def unpickle:SyncableSet[Syncable] = {
    val set = super.unpickle.asInstanceOf[SyncableSet[Syncable]]
    App.app.enableChanges(false) {
      loadRefs(set, members) foreach {             
        set += _
      }
    }
    set
  }
    
  def reviseCollection(change:CollectionChange):PickledSet = {
    val revisedMembers = 
      change match {
        case put:PutChange =>
          members + put.newVal
        case remove:RemoveChange =>
          members - remove.oldVal
        case clear:ClearChange =>
          PickledSet.emptyMembers
        case _ =>
          throw new ImplementationError      
      }
    new PickledSet(id, change.versionChange.now, properties, watches, revisedMembers)
  } 
  override def addWatch(watch:PickledWatch):PickledSet= {
    val moreWatches = watches + watch
    val pickled = new PickledSet(id, instanceVersion, properties, moreWatches, members)    
    trace2("PickledSet addWatch() %s", pickled)    
    pickled
  }
  
  override def deleteWatch(watch:PickledWatch):PickledSet = {
    val moreWatches = watches - watch
    val pickled = new PickledSet(id, instanceVersion, properties, moreWatches, members)    
    trace2("PickledSet deletewatch() %s", pickled)    
    pickled
  }

}
    
@serializable
class PickledMap(id:KindVersionedId, ver:String, 
    props:Map[String,SyncableValue], watches:Set[PickledWatch],
    val members:Map[Serializable, SyncableReference]) 
    extends Pickled(id,ver,props,watches) with PickledCollection[(Serializable,SyncableReference)] {

	implicit private val log = logger("pickledMap")

  override def unpickle:SyncableMap[Serializable, Syncable] = {
    val map = super.unpickle.asInstanceOf[SyncableMap[Serializable, Syncable]]
    App.app.enableChanges(false) {
      loadMapRefs(map, members) foreach {
        case (key, value) => map(key) = value
      }
    }
    map
  }
    
  def reviseCollection(change:CollectionChange):PickledMap = {
    val revisedMembers = 
      change match {
        case putMap:PutMapChange =>
          members + (putMap.key -> putMap.newValue)
        case removeMap:RemoveMapChange =>
          members - removeMap.key
        case clear:ClearChange =>
          PickledMap.emptyMembers
        case _ =>
          throw new ImplementationError      
      }
    new PickledMap(id, change.versionChange.now, properties, watches, revisedMembers)
  }
  override def addWatch(watch:PickledWatch):PickledMap = {
    val moreWatches = watches + watch
    val pickled = new PickledMap(id, instanceVersion, properties, moreWatches, members)    
    trace2("PickledMap addWatch() %s", pickled)    
    pickled
  }
  
  // SCALA - can we DRY these, maybe with 2.8 copy named parameters?
  override def deleteWatch(watch:PickledWatch):PickledMap = {
    val moreWatches = watches - watch
    val pickled = new PickledMap(id, instanceVersion, properties, moreWatches, members)    
    trace2("PickledMap deletewatch() %s", pickled)    
    pickled
  }

}
    
