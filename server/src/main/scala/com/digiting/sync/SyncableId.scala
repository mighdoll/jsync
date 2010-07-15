package com.digiting.sync
import collection.immutable


object CompositeId {
  val compositeIdRegex = """([a-zA-Z0-9_\.]+)/(.*)""".r
  def toSyncableId(compositeId:String):Option[SyncableId] = {
    compositeIdRegex.unapplySeq(compositeId) match {
      case Some(part :: id :: nil) => Some(SyncableId(PartitionId(part),id))
      case _ => None
    }
  }
}

import CompositeId.compositeIdRegex
object SyncableId {
  def unapply(s:String):Option[SyncableId] = {
    compositeIdRegex.unapplySeq(s) match {
      case Some(part :: id :: nil) => Some(SyncableId(PartitionId(part),id))
      case _ => None
    }
  }
  def apply(partitionId:PartitionId, instanceIdString:String) = 
    new SyncableId(partitionId, InstanceId(instanceIdString))
  def apply(partitionId:PartitionId, instanceId:InstanceId) = 
    new SyncableId(partitionId, instanceId)
}

class SyncableId(val partitionId:PartitionId, val instanceId:InstanceId) {  
  def toJsonMap = immutable.Map("$id" -> instanceId.id, "$partition" -> partitionId.id)
  def toJson = JsonUtil.toJson(toJsonMap)
  def toCompositeIdString = partitionId.id + "/" + instanceId.id
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
  def apply(partitionId:String, instanceId:String, kind:SyncManager.Kind) = 
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

import SyncManager.Kind
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
   override def toString = toCompositeIdString +  "[" + kind + "-" + kindVersion + "]"
} 
  
