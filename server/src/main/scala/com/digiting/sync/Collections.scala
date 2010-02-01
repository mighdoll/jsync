/*   Copyright [2009] Digiting Inc
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

import collection._
import com.digiting.sync.aspects.Observable
import net.lag.logging.Logger

/**
 * SyncableCollections are wrappers over collections with extensions to
 * work with the Observers and Syncable systems.
 * 
 * They call Observers.notify when they're changed.  They also give an
 * easy way to find their references.  
 * 
 * They're syncable objects themselves (so they can be transmitted to javascript).
 */
trait SyncableCollection extends Syncable {
  def syncableElements:Seq[Syncable]
      
  /** use reference equality */
  override def equals(other:Any):Boolean = {
    other match {
      case null => false
      case collection:SyncableCollection => collection eq this
      case _ => false
    }
  }
  override def hashCode() = {
    id.hashCode + partition.partitionId.hashCode
  }
  
  private[sync] def syncableElementIds:Seq[SyncableId] = {
    syncableElements map {_.fullId} toSeq
  }
}

class SyncableSet[T <: Syncable] extends mutable.Set[T] with SyncableCollection {
  val log = Logger("SyncableSet")
  def kind = "$sync.set"  
  val set = new mutable.HashSet[T] 
  
  def -=(elem:T) = {
    set -= elem
    Observers.notify(new RemoveChange(fullId, elem.fullId, newVersion()));
  }
  
  def +=(elem:T) = {
    set += elem
    val putChange = PutChange(fullId, elem.fullId, newVersion())
    log.trace("put: %s", putChange)
    Observers.notify(putChange);
  }
  


  def contains(elem:T) = set contains elem
  def size = set size
  def elements = set elements
    
  override def clear() = {
    val cleared = syncableElementIds
    set.clear();
    Observers.notify(new ClearChange(fullId, cleared, this.newVersion()));
  }

  def syncableElements:Seq[Syncable] = {
    val list = new mutable.ListBuffer[Syncable]
    for (elem <- elements) {
      elem match {
        case syncable:Syncable => list + syncable
        case _ =>
      }
    }
    list.toList
  }
}

/** a collection of objects in sequential order.  
 * 
 * Not too worried about the API for now, this changes with scala 2.8
 */
class SyncableSeq[T <: Syncable] extends SyncableCollection {
  def kind = "$sync.sequence"
  val list = new mutable.ArrayBuffer[T]
  
  def syncableElements:Seq[Syncable] = {
    list.clone
  }
  
  def insert(index:Int, elem:T) = {
    list.insert(index, elem)
    Observers.notify(new InsertAtChange(this.fullId, elem.fullId, index, newVersion()))
  }
  
  def remove(index:Int) = {
    val origValue = list(index)
    list.remove(index)
    Observers.notify(new RemoveAtChange(this.fullId, index, origValue.fullId, newVersion()))    
  }
  
  def toStream = list.toStream
  
  def length = list.length
  def apply(index:Int) = list.apply(index)  
  def first = list.first
  def last = list.last
  def firstOption = list.firstOption
  def lastOption = list.lastOption  
  
  def clear() = {
    val cleared = syncableElementIds
    list.clear();
    Observers.notify(new ClearChange(this.fullId, cleared, newVersion()));
  }
  
  def map[C](fn: (T)=> C):Seq[C] = list.map(fn)
  
  def zipWithIndex:Array[(T,Int)]= {
    var i = 0
    val buf = new mutable.ArrayBuffer[(T,Int)]
    while (i < list.size) {
      buf += (list(i), i)
      i += 1
    }
    buf.toArray
  }
  
  def +=(elem:T) = insert(length, elem)
  def ++=(toAdd:Iterable[T]) = toAdd map (this += _)
  
  def move(fromDex:Int, toDex:Int) {
    val elem = list(fromDex)
    list.remove(fromDex)
    list.insert(toDex, elem)            
    Observers.notify(new MoveChange(this.fullId, fromDex, toDex, newVersion()))
  }
  
  def toList = list.toList
}

/** A collection of syncable objects 
 * Not fully implemented or tested
 */
class SyncableMap[A,B] extends mutable.Map[A,B] with SyncableCollection {
  def kind = "$sync.map"
  val map = new mutable.HashMap[A,B]
  

  def -=(key:A) = {
    map -= key
    Observers.notify(new RemoveMapChange(this.fullId, key, get(key), newVersion()))
  }
  
  def update(key:A, value:B) = {
    map.update(key, value)
    Observers.notify(new UpdateMapChange(this.fullId, key, value, newVersion() ))
  }
  
  def get(key:A):Option[B] = map get key
  
  def size = map size
    
  def elements = map elements
  
  def syncableElements:Seq[Syncable] = {
    val set = new mutable.HashSet[Syncable]
    for ((key,value) <- this) {
      ifSyncable(key) map (set + _)
      ifSyncable(value) map (set + _)
    }
    set.toList
  }

  /** return Some(syncable) if the value is of type Syncable */
  private def ifSyncable(value:Any):Option[Syncable] = {
    value match {
      case syncable:Syncable => Some(syncable)
      case _ => None
    }
  }
  
}
