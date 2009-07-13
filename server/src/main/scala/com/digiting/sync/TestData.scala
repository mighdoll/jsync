package com.digiting.sync
import SyncManager.NewSyncableIdentity
import com.digiting.sync.syncable._
import net.liftweb.util.Log

object TestData {
  val testPartition = new RamPartition("test")
  def setup() {
    val nameObj = 
      SyncManager.setNextId.withValue(NewSyncableIdentity("#testName1", testPartition)) {
    	new TestNameObj
      }

    SyncManager.currentPartition.withValue(testPartition) {
	  nameObj.name = "emmett"
	  testPartition.published.create("oneName", nameObj)
		
	  val set = new SyncableSet[Syncable]
	  val mercer = new TestNameObj
	  mercer.name = "mercer"
	  set + mercer
	  testPartition.published.create("oneSet", set)
		
	  val twoSet = new SyncableSet[Syncable]
	  testPartition.published.create("twoSet", twoSet)
		
	  Observers.watch(twoSet, twoSetChanged, "testTwoSet")
		
	  val modifyOneName = new TestNameObj
	  testPartition.published.create("modifyOneName", modifyOneName)
   
	  val oneParagraph = new TestParagraph
	  oneParagraph.text = "here's som initial text"
	  testPartition.published.create("testParagraph", oneParagraph)  
    }
  }
  
  def twoSetChanged(change:ChangeDescription) {
    change match {
      case put:PutChange if (change.source != "server-application") => mirrorClientPut(put)
      case _ => // ignore other changes
    }
  }
  
  def mirrorClientPut(change:PutChange) {
      SyncManager.currentPartition.withValue(testPartition) {
		Observers.currentMutator.withValue("server-application") {	// SOON this should be done by the framework
		  val name = new TestNameObj
		  name.name = "server-" + change.newValue.asInstanceOf[TestNameObj].name
		  change.target match {
		    case twoSet:SyncableSet[_] =>
		      twoSet.asInstanceOf[SyncableSet[Syncable]] += name       
		    case _ => 
		      Log.error("twoSetChanged() unexpected target of change: " + change.target)
		    }
        }
      }
  }
}
