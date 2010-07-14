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
import java.io.Serializable
import com.digiting.util._

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
    syncableElements map {_.id} toSeq
  }
  
  def revise(change:CollectionChange) 
}

import Log2._
/** a collection of objects in sequential order.  
 * 
 * Not too worried about the API for now, this changes with scala 2.8
 */
class SyncableSeq[T <: Syncable] extends SyncableCollection {
  def kind = "$sync.sequence"
  implicit private lazy val log = logger("SyncableSeq")
  val list = new mutable.ArrayBuffer[T]
  
  def syncableElements:Seq[Syncable] = {
    list.clone
  }
  
  override def revise(change:CollectionChange) {  
    trace2("revise: %s", change)
    change match {
      case insertAt:InsertAtChange =>
        val elem = App.app.get(insertAt.newVal) getOrElse abort2("revise() can't find find newVal %s", change)
      	insert(insertAt.at, elem.asInstanceOf[T])
      case removeAt:RemoveAtChange =>
        remove(removeAt.at)
      case _ => NYI()
    }
  }
  
  def insert(index:Int, elem:T)  {
    list.insert(index, elem)    
    App.app.updated(this) {InsertAtChange(this.id, SyncableReference(elem), index, newVersion())}
  }
  
  def remove(index:Int) = {
    val origValue = list(index)
    list.remove(index)
    App.app.updated(this) { RemoveAtChange(this.id, index, origValue.id, newVersion()) }
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
    App.app.updated(this) {ClearChange(this.id, cleared, newVersion())}
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
    App.app.updated(this) {MoveChange(this.id, fromDex, toDex, newVersion())}
  }
  
  def toList = list.toList
}

class SyncableSet[T <: Syncable] extends mutable.Set[T] with SyncableCollection {
  val log = Logger("SyncableSet")
  def kind = "$sync.set"  
  val set = new mutable.HashSet[T] 

  override def revise(change:CollectionChange) = NYI()

  def -=(elem:T) = {
    set -= elem
    App.app.updated(this) {RemoveChange(id, SyncableReference(elem), newVersion())}
  }
  
  def +=(elem:T) = {
    set += elem
    App.app.updated(this) {PutChange(id, SyncableReference(elem), newVersion())}
  }

  def contains(elem:T) = set contains elem
  def size = set size
  def elements = set elements
    
  override def clear() = {
    val cleared = syncableElementIds
    set.clear();
    App.app.updated(this) {ClearChange(id, cleared, this.newVersion())}
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

/** A collection of syncable objects.  Keys are typically strings, values are Syncable.
 */
class SyncableMap[K <: Serializable, V <: Syncable] extends mutable.HashMap[K,V] with SyncableCollection {
  def kind = "$sync.map"
  val log = Logger("SyncableMap")

  override def revise(change:CollectionChange) = NYI()

  override def update(key:K, value:V) {
    key match {
      case k:Syncable => throw new IllegalArgumentException
      case _ =>
    }
    val oldValueOpt = get(key) map {SyncableReference(_)}
    super.update(key,value)
    App.app.updated(this) {PutMapChange(this.id, key, oldValueOpt, 
        SyncableReference(value), newVersion() )}
  }
  
  override def clear {
    val members = syncableElements map {_.id}
    super.clear()
    App.app.updated(this) {ClearChange(this.id, members, newVersion())}
  }
  
  override def removeEntry(key:K):Option[Entry] = {
    val oldValue =  get(key) match {
      case Some(v:Syncable) =>
        SyncableReference(v)
      case x =>
        log.error("removeEntry() unexpected value type: " + x)
        throw new InternalError
    }
    
    val result = super.removeEntry(key)
    App.app.updated(this) {RemoveMapChange(this.id, key, oldValue, newVersion())}
    result
  }
  
  def syncableElements:Seq[Syncable] = {
    throw new NotYetImplemented
  }
}


