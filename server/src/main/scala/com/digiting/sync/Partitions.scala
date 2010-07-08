package com.digiting.sync
import com.digiting.util._
import collection.mutable.HashMap
import RandomIds.randomUriString
import collection.mutable

// SOON change get(), remove() to use PartitionId
object Partitions extends LogHelper {
  val log = logger("Partitions")
  val localPartitions = new HashMap[String, Partition]    // Synchronize?
  def get(name:String):Option[Partition] = localPartitions get name
  def add(partition:Partition) = localPartitions += (partition.id.id -> partition)
  
  def getMust(name:String):Partition = {
    get(name) getOrElse {
      abort("user Partition %s not found", name)
    }
  }
  
  def apply(syncableId:SyncableId):Partition = {
    getMust(syncableId.partitionId.id)
  }
  
  def apply(partitionId:PartitionId):Partition = {
    getMust(partitionId.id)
  }
  
  def remove(name:String) = {
    localPartitions -= name
  }
    
  // LATER, create strategy for handling remote partitions
}


object Partition extends LogHelper {
  lazy val log = logger("Partition(Obj)")
  class Transaction { // LATER move this so each partition subclass can implement their own
    val id = randomUriString(8)
    val changes = new mutable.ListBuffer[StorableChange]
  }
  
  class InvalidTransaction(message:String) extends Exception(message) {
    def this() = this("")
  }
  
}

case class PartitionId(val id:String) {
  override def toString:String = id
}

case class InstanceId(val id:String) {
  override def toString:String = id
}

