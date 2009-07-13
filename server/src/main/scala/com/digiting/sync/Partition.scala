package com.digiting.sync

import scala.util.DynamicVariable
import net.liftweb.util.Log
import scala.actors.Actor
import scala.actors.Actor._
import JsonObject._
import collection.mutable


/** A storage segment of syncable objects 
 * 
 * Put() and update() operations are asynchronous and eventually consistent.  
 * so get() might not return the most recently put() data (e.g. when servers fail and recover).
 * 
 * The current implementation does not guarantee durability.  If the server crashes right after
 * put() is called, the data is lost.
 */
abstract class Partition(val partitionId:String) {
  def get(instanceId:String):Option[Syncable] 
  def put(syncable:Syncable):Unit
  def delete(instanceId:String):Unit
  def update(change:PropertyChange):Unit  
  val published = new PublishedRoots(this)
  
  Partitions.add(this)
  
  // LATER make this is a transactional interface, see Partition2 for an early sketch
}

abstract class SimpleDbPartition(partId:String) extends Partition(partId) {  
}


class RamPartition(partId:String) extends Partition(partId) {
  val store = new mutable.HashMap[String,Syncable]
  
  def get(instanceId:String):Option[Syncable] = store get instanceId 
  def put(syncable:Syncable) = store put (syncable.id, syncable)
  def delete(instanceId:String) = store -= instanceId
  def update(change:PropertyChange) = {} // change should already be applied to object, because it's in RAM
}

import collection._
object Partitions {
  val localPartitions = new mutable.HashMap[String,Partition]
  def get(name:String):Option[Partition] = localPartitions get name
  def add(partition:Partition) = localPartitions + (partition.partitionId -> partition)
  
  add(new RamPartition("default"))	// CONSIDER don't really want a default partition do we?  is this needed?
  
  // LATER, create strategy for handling remote partitions
}
