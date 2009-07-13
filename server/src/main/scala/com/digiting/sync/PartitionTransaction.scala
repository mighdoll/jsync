package com.digiting.sync
import collection._
import JsonObject._

/**
 * A group of CRUD operations on syncable objects.  Used by
 * the Partition to bundle up an atomic transaction of changes.
 */
class PartitionTransaction {
  // log of changes in this transaction
  val puts = new mutable.Queue[JsonMap]
  val edits = new mutable.Queue[ChangeDescription]

  /** add a create operation to the log */
  def add(frozenObj:JsonMap) = puts += frozenObj
  
  /** add an update to the log */
  def edit(change:ChangeDescription) = edits += change
  
  /** apply all changes to the specified pool of instances */
  def apply(pool:InstancePool) = {
    throw new NotYetImplemented    
  }
  
  /** erase this.  CONSIDER is this needed? */
  def clear() = {
    edits.clear
    puts.clear
  }  
}
