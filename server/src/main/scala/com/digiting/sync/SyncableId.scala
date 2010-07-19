package com.digiting.sync
import collection.immutable

import SyncableKinds.Kind

object CompositeId {
  val compositeIdRegex = """([a-zA-Z0-9_\.]+)/(.*)""".r
  def toSyncableId(compositeId:String):Option[SyncableId] = {
    compositeIdRegex.unapplySeq(compositeId) match {
      case Some(part :: id :: nil) => Some(SyncableId(PartitionId(part),id))
      case _ => None
    }
  }
  def compositeString(partitionId:String, instanceId:String):String = {
    partitionId + "/" + instanceId
  }
}

import CompositeId._
object SyncableId {
  def unapply(s:String):Option[SyncableId] = toSyncableId(s)
  
  def apply(partitionId:PartitionId, instanceIdString:String) = 
    new SyncableId(partitionId, InstanceId(instanceIdString))
  def apply(partitionId:PartitionId, instanceId:InstanceId) = 
    new SyncableId(partitionId, instanceId)
  def apply(partitionIdString:String, instanceIdString:String) = 
    new SyncableId(PartitionId(partitionIdString), InstanceId(instanceIdString))
  
}

class SyncableId(val partitionId:PartitionId, val instanceId:InstanceId) {  
  def toJsonMap = immutable.Map("$id" -> instanceId.id, "$partition" -> partitionId.id)
  def toJson = JsonUtil.toJson(toJsonMap)
  def toCompositeIdString = compositeString(partitionId.id, instanceId.id)
  override def toString = toCompositeIdString
  def target = App.app.get(this)
  override def equals(other: Any): Boolean = 
    other match {
      case o:SyncableId => 
        (o canEqual this) && 
          (partitionId == o.partitionId) && (instanceId == o.instanceId) 
      case _ => false
  }
  def canEqual(other:Any):Boolean = other.isInstanceOf[SyncableId]
  override def hashCode:Int = 41 * (41+partitionId.hashCode) + instanceId.hashCode
}

/** a persistent reference to a syncable */
object SyncableReference {
  def apply(partitionId:String, instanceId:String, kind:Kind) = 
    new SyncableReference(PartitionId(partitionId), InstanceId(instanceId), kind)    
  
  
  def apply(id:SyncableId):SyncableReference = {
    App.app.withGetId(id) {obj => 
      SyncableReference(obj)
    }
  }
  
  def apply(syncable:Syncable):SyncableReference = {
    new SyncableReference(syncable.id.partitionId, syncable.id.instanceId, syncable.kind)
  }
}

class SyncableReference(partitionId:PartitionId, instanceId:InstanceId, val kind:Kind) extends 
  SyncableId(partitionId, instanceId) {
  override def toString = super.toString + "[" + kind + "]"  
}

object KindVersionedId {
  def apply(syncable:Syncable):KindVersionedId = 
    new KindVersionedId(syncable.id.partitionId, syncable.id.instanceId, syncable.kind, syncable.kindVersion) 

}

/** a syncable id that includes a kind and a kindVersion */
class KindVersionedId(partitionId:PartitionId, instanceId:InstanceId, kind:Kind, val kindVersion:String) 
	extends SyncableReference(partitionId, instanceId, kind) {
   override def toString = toCompositeIdString +  "[" + kind + ":" + kindVersion + "]"
} 
 

trait HasId {
  val id:String
  override def toString:String = id  
}

/** id of an instance within a partition */
case class InstanceId(val id:String) extends HasId 

/** id of a storage partition */
case class PartitionId(val id:String) extends SyncNode with HasId 

/** id of a client browser connection */
case class ClientId(val id:String) extends SyncNode with HasId 

/** id of a server app context */
case class AppId(val id:String) extends SyncNode with HasId 

/** a participant in the app synchronization */
trait SyncNode

