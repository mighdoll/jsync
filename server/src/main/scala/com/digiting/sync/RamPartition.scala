package com.digiting.sync
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Buffer
import com.digiting.util.MultiBuffer
import com.digiting.util.MapMap
import collection.mutable.HashMap
import collection.mutable.HashSet
import collection.mutable.Set
import scala.collection.mutable.MultiMap
import net.lag.logging.Logger
import com.digiting.util.LogHelper
import scala.collection.immutable
import java.io.Serializable

import Partition._

class RamPartition(partId:String) extends Partition(partId) with LogHelper {
  protected val log = Logger("RamPartition")
  private val store = new HashMap[String, Pickled[Syncable]]
  private val seqMembers = new HashMap[String, Buffer[SyncableReference]] 
    with MultiBuffer[String, SyncableReference]
  private val setMembers = new HashMap[String, Set[SyncableReference]] 
    with MultiMap[String, SyncableReference]
  private val mapMembers = new HashMap[String, HashMap[Serializable, SyncableReference]] 
    with MapMap[String, Serializable, SyncableReference]
  def commit(tx:Transaction) {}
  def rollback(tx:Transaction) {}
  
  def get[T <: Syncable](instanceId:String, tx:Transaction):Option[Pickled[T]] = {    
    store get instanceId map {_.asInstanceOf[Pickled[T]]}    
  }
  
  def update(change:DataChange, tx:Transaction) = change match {
    case created:CreatedChange[_] => 
      put(created.pickled)
    case prop:PropertyChange =>
      get(prop.target.instanceId, tx) orElse {  
        err("target of property change not found: %s", prop) 
      } foreach {pickled =>
        pickled.update(prop)
        put(pickled)
      }
    case deleted:DeletedChange =>
      throw new NotYetImplemented
    case clear:ClearChange =>      
      // we don't know the type of the target, so clear 'em all.  CONSIDER: should dataChange.target a SyncableReference?
      seqMembers -= clear.target.instanceId
      setMembers -= clear.target.instanceId
      mapMembers -= clear.target.instanceId
    case put:PutChange =>
      setMembers.add(put.target.instanceId, put.newVal)
    case remove:RemoveChange =>
      setMembers.remove(remove.target.instanceId, remove.oldVal)
    case move:MoveChange =>
      val moving = seqMembers.remove(move.target.instanceId, move.fromDex)
      seqMembers.insert(move.target.instanceId, moving, move.toDex)
    case insertAt:InsertAtChange =>
      seqMembers.insert(insertAt.target.instanceId, insertAt.newVal, insertAt.at)
    case removeAt:RemoveAtChange =>
      seqMembers.remove(removeAt.target.instanceId, removeAt.at)
    case putMap:PutMapChange =>
      mapMembers.update(putMap.target.instanceId, (putMap.key -> putMap.newValue))
    case removeMap:RemoveMapChange =>
      mapMembers.remove(removeMap.target.instanceId, removeMap.key)
  }
  
  private[this] def put[T <: Syncable](pickled:Pickled[T]) {
    store += (pickled.reference.instanceId -> pickled.asInstanceOf[Pickled[Syncable]])
  }
  
  def getSeqMembers(instanceId:String, tx:Transaction):Option[Seq[SyncableReference]] = {    
    seqMembers get instanceId 
  }
  def getSetMembers(instanceId:String, tx:Transaction):Option[immutable.Set[SyncableReference]] = {    
    setMembers get instanceId map {mutableSet => immutable.Set() ++ mutableSet}
  }
  def getMapMembers(instanceId:String, tx:Transaction):Option[Map[Serializable,SyncableReference]] = {
    mapMembers get instanceId map {mutableMap => immutable.Map() ++ mutableMap}
  }

  def deleteContents() {
    for {(id, pickled) <- store} {
      log.trace("deleting: %s", pickled)
      store -= (id)
    }
  }

  override def debugPrint() {
    for {(_,value) <- store} {
      log.info("  %s", value.toString)
    }
  }
}
