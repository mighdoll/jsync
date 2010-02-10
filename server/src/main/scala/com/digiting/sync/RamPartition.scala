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
import scala.collection.{mutable, immutable}
import java.io.Serializable
import Partition._

class RamPartition(partId:String) extends Partition(partId) with LogHelper {
  protected val log = Logger("RamPartition")
  private val store = new HashMap[String, Pickled]
  
  def commit(tx:Transaction) {}
  def rollback(tx:Transaction) {}
  
  def get(instanceId:String, tx:Transaction):Option[Pickled] = synchronized {    
    val result = store get instanceId     
    log.trace("get %s, found: %s", instanceId, result getOrElse "")
    result
  }
  
  def update(change:DataChange, tx:Transaction) = synchronized {
    val instanceId = change.target.instanceId
    
    change match {
      case created:CreatedChange => 
        put(created.pickled)
      case prop:PropertyChange =>
        get(instanceId, tx) orElse {  
          err("target of property change not found: %s", prop) 
        } foreach {pickled =>
          store(instanceId) = pickled.revise(prop)
        }
      case deleted:DeletedChange =>
        throw new NotYetImplemented
      case clear:ClearChange => 
        store get instanceId orElse {
          err("update() ClearChange target not found: %s", clear.target)
          throw new ImplementationError
        } foreach {pickled =>
          pickled match {
            case pickledCollection:PickledCollection =>
              val cleared = 
                pickledCollection match {
                  case seq:PickledSeq =>
                    PickledSeq(pickled, PickledSeq.emptyMembers)
                  case set:PickledSet =>
                    PickledSet(pickled, PickledSet.emptyMembers)
                  case map:PickledMap =>
                    PickledMap(pickled, PickledMap.emptyMembers)
                }
              store(instanceId) = cleared
            case _ =>
              throw new ImplementationError
          }
        }
      case put:PutChange =>
        val set = expectSet(instanceId)
        val revisedMembers = set.members + put.newVal
        store(instanceId) = PickledSet(set, revisedMembers)
      case remove:RemoveChange =>
        val set = expectSet(instanceId)
        val revisedMembers = set.members - remove.oldVal
        store(instanceId) = PickledSet(set, revisedMembers)
      case insertAt:InsertAtChange =>
        val seq = expectSeq(instanceId)
        val revisedMembers = seq.members.clone
        revisedMembers insert(insertAt.at, insertAt.newVal)
        store(instanceId) = PickledSeq(seq, revisedMembers)
      case removeAt:RemoveAtChange =>
        val seq = expectSeq(instanceId)
        val revisedMembers = seq.members.clone
        val moving = revisedMembers remove(removeAt.at)
        store(instanceId) = PickledSeq(seq, revisedMembers)
      case move:MoveChange =>
        val seq = expectSeq(instanceId)
        val revisedMembers = seq.members.clone
        val moving = revisedMembers remove(move.fromDex)
        revisedMembers insert(move.toDex, moving)
        store(instanceId) = PickledSeq(seq, revisedMembers)
      case putMap:PutMapChange =>
        val map = expectMap(instanceId)
        val revisedMembers = map.members + (putMap.key -> putMap.newValue)
        store(instanceId) = PickledMap(map, revisedMembers)
      case removeMap:RemoveMapChange =>
        val map = expectMap(instanceId)
        val revisedMembers = map.members - removeMap.key
        store(instanceId) = PickledMap(map, revisedMembers)
    }
  }
  
  private[this] def expectSeq(instanceId:String):PickledSeq = {
    store get instanceId match {
      case Some(pickledSeq:PickledSeq) => pickledSeq
      case _ =>
        err("can't find seq collection for: %s", instanceId)
        throw new ImplementationError
    }
  }
  
  private[this] def expectSet(instanceId:String):PickledSet = {
    store get instanceId match {
      case Some(pickledSet:PickledSet) => pickledSet
      case _ =>
        err("can't find set collection for: %s", instanceId)
        throw new ImplementationError
    }
  }
  private[this] def expectMap(instanceId:String):PickledMap = {
    store get instanceId match {
      case Some(pickledMap:PickledMap) => pickledMap
      case _ =>
        err("can't find map collection for: %s", instanceId)
        throw new ImplementationError
    }
  }
  
  private[this] def put[T <: Syncable](pickled:Pickled) = synchronized {
    store += (pickled.reference.instanceId -> pickled)
  }
  

  def deleteContents() = synchronized {
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
