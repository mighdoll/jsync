package com.digiting.sync
import SyncManager.withGetId
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
  def target = SyncManager.get(this)
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
  def apply(partitionId:String, instanceId:String, kind:SyncManager.Kind) = {
    val id = SyncableId(PartitionId(partitionId), InstanceId(instanceId))
    new SyncableReference(id, kind)    
  }
  
  def apply(id:SyncableId) = {
    withGetId(id) {obj => 
      new SyncableReference(id, obj.kind)
    }
  }
  
  def apply(syncable:Syncable) = {
    new SyncableReference(syncable.fullId, syncable.kind)
  }
}

class SyncableReference(val id:SyncableId, val kind:SyncManager.Kind) extends 
  SyncableId(id.partitionId, id.instanceId) {
  override def toString = id.toCompositeIdString + "[" + kind + "]"
}
