package com.digiting.sync
import com.digiting.util.{InifinispanCache, LogHelper}
import net.lag.logging.Logger
import Partition._
import collection.immutable
import java.io.Serializable

class InfinispanPartition(partId:String) extends Partition(partId) with LogHelper {
  protected val log = Logger("InfinispanPartition")
  private val store = new InifinispanCache[String, Pickled]

  def commit(tx:Transaction) {}
  def rollback(tx:Transaction) {}
  

  def get(instanceId:String, tx:Transaction):Option[Pickled] = {    
    store get instanceId 
  }

  def update(change:DataChange, tx:Transaction) = change match {
    case created:CreatedChange => 
      NYI()
//      put(created.pickled)
    case prop:PropertyChange =>
      NYI()
      get(prop.target.instanceId, tx) orElse {  
        err("target of property change not found: %s", prop) 
      } foreach {pickled =>
        pickled.update(prop)
//        put(pickled)
      }
    case deleted:DeletedChange =>
      throw new NotYetImplemented
    case clear:ClearChange =>      
      NYI()
      // we don't know the type of the target, so clear 'em all.  CONSIDER: should dataChange.target a SyncableReference?
//      seqMembers -= clear.target.instanceId
//      setMembers -= clear.target.instanceId
//      mapMembers -= clear.target.instanceId
    case put:PutChange =>
      NYI()
//      setMembers.add(put.target.instanceId, put.newVal)
    case remove:RemoveChange =>
      NYI()
//      setMembers.remove(remove.target.instanceId, remove.oldVal)
    case move:MoveChange =>
      NYI()
//      val moving = seqMembers.remove(move.target.instanceId, move.fromDex)
//      seqMembers.insert(move.target.instanceId, moving, move.toDex)
    case insertAt:InsertAtChange =>
      NYI()
//      seqMembers.insert(insertAt.target.instanceId, insertAt.newVal, insertAt.at)
    case removeAt:RemoveAtChange =>
      NYI()
//      seqMembers.remove(removeAt.target.instanceId, removeAt.at)
    case putMap:PutMapChange =>
      NYI()
//      mapMembers.update(putMap.target.instanceId, (putMap.key -> putMap.newValue))
    case removeMap:RemoveMapChange =>
      NYI()
//      mapMembers.remove(removeMap.target.instanceId, removeMap.key)
  }
  
  def deleteContents() {
    NYI()
  }

  def getSeqMembers(instanceId:String, tx:Transaction):Option[Seq[SyncableReference]] = {    
    NYI()
//    seqMembers get instanceId 
  }
  def getSetMembers(instanceId:String, tx:Transaction):Option[immutable.Set[SyncableReference]] = {    
    NYI()
//    setMembers get instanceId map {mutableSet => immutable.Set() ++ mutableSet}
  }
  def getMapMembers(instanceId:String, tx:Transaction):Option[Map[Serializable,SyncableReference]] = {
    NYI()
//    mapMembers get instanceId map {mutableMap => immutable.Map() ++ mutableMap}
  }
  
}
