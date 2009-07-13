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
      case s:SyncableSet[_] => s eq this
      case _ => false
    }
  }
  override def hashCode() = {
    id.hashCode + partition.partitionId.hashCode
  }
}

class SyncableSet[T <: Syncable] extends mutable.Set[T] with SyncableCollection {
  def kind = "$sync.set"  
  val set = new mutable.HashSet[T] 
  
  def -=(elem:T) = {
    Observers.notify(new RemoveChange(this, elem));
    set -= elem
  }
  
  def +=(elem:T) = {
    Observers.notify(new PutChange(this, elem));
    set += elem
  }
  
  def contains(elem:T) = set contains elem
  def size = set size
  def elements = set elements

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
 * Internally, the elements are each assigned a sort key.  
 */
class SyncableSequence extends Syncable {
  def kind = "$sync.sequence"
}

/** A collection of syncable objects */
class SyncableMap[A,B] extends mutable.Map[A,B] with SyncableCollection {
  def kind = "$sync.map"
  val map = new mutable.HashMap[A,B]
  

  def -=(key:A) = {
    Observers.notify(new RemoveMapChange(this, key, get(key)))
    map -= key
  }
  
  def update(key:A, value:B) = {
    Observers.notify(new UpdateMapChange(this, key, value))
    map.update(key, value)
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