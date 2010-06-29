package com.digiting.sync
import Observers.DataChangeFn
import com.digiting.util._


abstract class Partition2(val id:String) extends LogHelper {  
  protected lazy val log = logger("Partition")
  
  def get(id:InstanceId):Option[Syncable] = None
  def modify(change:DataChange) {}
  def watch(id:InstanceId, fn:DataChangeFn) {}
  val published:Published = new EmbeddedPublished
}
