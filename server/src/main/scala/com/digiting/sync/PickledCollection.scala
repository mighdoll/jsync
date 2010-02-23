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
import net.lag.logging.Logger
import com.digiting.util._
import scala.collection.{mutable,immutable}
import java.io.Serializable
import scala.collection.mutable.ArrayBuffer
import LoadReferences._

abstract trait PickledCollection extends Pickled {
  def revise(change:CollectionChange):PickledCollection = {
    assert(version == change.versionChange.old)
    reviseCollection(change)
  }
  
  def reviseCollection(change:CollectionChange):PickledCollection 
}

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
    new PickledSeq(reference, change.versionChange.now, properties, revisedMembers)
  }  
}
    
@serializable
class PickledSet(ref:SyncableReference, ver:String, 
    props:Map[String,SyncableValue], val members:Set[SyncableReference]) 
    extends Pickled(ref,ver,props) with PickledCollection {
      
  override def unpickle:SyncableSet[Syncable] = {
    val set = super.unpickle.asInstanceOf[SyncableSet[Syncable]]
    Observers.withNoNotice {
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
    new PickledSet(reference, change.versionChange.now, properties, revisedMembers)
  } 

}
    
@serializable
class PickledMap(ref:SyncableReference, ver:String, 
    props:Map[String,SyncableValue], val members:Map[Serializable, SyncableReference]) 
    extends Pickled(ref,ver,props) with PickledCollection {
      
  override def unpickle:SyncableMap[Serializable, Syncable] = {
    val map = super.unpickle.asInstanceOf[SyncableMap[Serializable, Syncable]]
    Observers.withNoNotice {
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
    new PickledMap(reference, change.versionChange.now, properties, revisedMembers)
  } 

}
    
