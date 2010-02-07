package com.digiting.sync.test
import scala.collection.mutable
import com.digiting.util.Configuration


trait SyncFixture {  
  val changes = new mutable.ListBuffer[ChangeDescription]()
  val testPartition = new RamPartition("testPartition2")
  
  def withTestFixture[T](fn: => T):T = {
    setup()
    
    val result:T = fn
    cleanup()
    result
  }
   
  def cleanup() {
    Observers.unwatchAll(this)
    Observers.reset()
    SyncManager.reset()
  }
  
  private def setup() {
    Configuration.initFromVariable("jsyncServerConfig")      
    changes.clear()
    SyncManager.currentPartition.value = testPartition
  }

    
  private def changed(change:ChangeDescription) {
    changes + change
  }

}