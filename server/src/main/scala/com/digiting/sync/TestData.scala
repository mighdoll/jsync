/*   Copyright [2009] Digiting Inc
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.digiting.sync
import com.digiting.sync.syncable._
import com.digiting.sync.aspects.Observable
import net.lag.logging.Logger
import Observers.watch

/** Embedded test server for client tests.  
 * SOON: refactor this into an application 
 */
object TestData {
  var log = Logger("TestData")
  var testPartition:Partition = _
  def setup() {
	testPartition = new RamPartition("test")
    val nameObj = 
      SyncManager.setNextId.withValue(SyncableIdentity("#testName1", testPartition)) {
    	new TestNameObj
      }

    // setup up some static and dynamically genreated test objects
    withTestPartition {
	  nameObj.name = "emmett"
	  testPartition.published.create("oneName", nameObj)
		
	  val set = new SyncableSet[Syncable]
	  set += TestNameObj("mercer")
	  testPartition.published.create("oneSet", set)
		
	  val twoSet = new SyncableSet[Syncable]
	  testPartition.published.create("twoSet", twoSet)		
	  Observers.watch(twoSet, twoSetChanged, "testTwoSet")
		
	  testPartition.published.create("modifyOneName", new TestNameObj)   
	  testPartition.published.create("testParagraph", new TestParagraph("here's some initial text"))  
   
	  val sequence = new SyncableSeq[Syncable]
	  testPartition.published.create("sequence", sequence)  
      Observers.watch(sequence, sequenceChanged, "testSequence");
      
	  testPartition.published.createGenerated("moveSequence", createMoveSequence)  
	  testPartition.published.createGenerated("removeSequence", createRemoveSequence)  
	  testPartition.published.createGenerated("modifyReference", createModifyReference)  
	  testPartition.published.createGenerated("serverSequenceAdd", createSequenceAdd)  
    }
  }
  
  def withTestPartition[T](fn: => T):T = {
    SyncManager.currentPartition.withValue(testPartition) {
     fn
    }    
  }  
  
  def createSequenceAdd():Option[Syncable] = {
    val ref = withTestPartition { new TestRefObj }    
    watch(ref, changed, "createSequenceMod")    
    
    def changed(change:ChangeDescription) {
      if (change.source != "test-application") {
        log.trace("createSequenceAdd: %s", change)
        val seq = withTestPartition  {new SyncableSeq[TestNameObj]}
        Observers.currentMutator.withValue("test-application") {	// SOON this should be done by the framework
          ref.ref = seq
          seq += withTestPartition {new TestNameObj("don't duplicate me")}
        }
      }
    }
    
    Some(ref)
  }
  
  def createModifyReference():Option[Syncable] = {
    val ref = withTestPartition {new TestRefObj}    
    watch(ref, refChange, "testModifyReference")
    Some(ref)
  }
  
  def refChange(change:ChangeDescription) = {
    change match {
      case modify:PropertyChange if change.source != "server-application" =>
        change.target match {
          case root:TestRefObj =>
            root.ref match {
              case clientRef:TestRefObj if (clientRef.ref == root) =>      // only insert one time
                Observers.currentMutator.withValue("server-application") {	// SOON this should be done by the framework
                  // root -> clientNewRef -> root   
                  val serverRef = withTestPartition {new TestRefObj(clientRef)}
                  root.ref = serverRef               
                  // root -> serverNewRef -> clientNewRef -> root
                }
                SyncManager.instanceCache.commit()	// TODO - not necessary?
              case clientRef:TestRefObj =>
              case _ =>
                 log.error("Test.refChange() unexpected change made by client: not a ref: " + root.ref);
             }
          case _ =>
              log.error("Test.refChange() root changed, not a TestRefObj!");
        }
      case _ =>
    }
  }
  
  def createRemoveSequence():Option[Syncable] = {
    val seq = createNameSequence("a","b","c")
    Observers.watch(seq, removeSequenceChanged, "testSequence");      
    Some(seq)    
  }
  
  private def removeSequenceChanged(change:ChangeDescription) {
    change match {
      case remove:RemoveAtChange if change.source != "server-application" =>
      	Observers.currentMutator.withValue("server-application") {	// SOON this should be done by the framework
          val seq = remove.target.asInstanceOf[SyncableSeq[TestNameObj]]
          seq.remove(1)
        }
      case _ =>
    }
  }
  
  
  private def createNameSequence(names: String*):SyncableSeq[TestNameObj] = {
    withTestPartition {
      val seq = new SyncableSeq[TestNameObj]
      for (name <-names)
        seq += TestNameObj(name)    
      seq
    }
  }
  
  def createMoveSequence():Option[Syncable] = {
    val moveSequence = createNameSequence("a","b","c")
    Observers.watch(moveSequence, moveSequenceChanged, "testSequence");      
    Some(moveSequence)
  }
  
  def moveSequenceChanged(change:ChangeDescription) {
    log.debug("moveSequnceChanged: %s", change.toString)
    change match {
      case move:MoveChange if (change.source != "server-application") =>
        moveSeq(move.target)
      case _ => // ignore
    } 
  }
  
  def moveSeq(possibleSeq:Observable) {
    Observers.currentMutator.withValue("server-application") {	// SOON this should be done by the framework
  	  possibleSeq match {
	    case seq:SyncableSeq[_] =>
	      seq.move(2, 0)
          SyncManager.instanceCache.commit()	// TODO - not necessary?
        case _ =>
          log.error("moveSeq() sequence move test problem, not a seq: " + possibleSeq)	    
	  }
    }
  }
  
  
  
  def sequenceChanged(change:ChangeDescription) {
    change match {
      case insert:InsertAtChange if (change.source != "server-application") =>
        modifySeq(insert)
      case _ => // ignore other changes
    }     
  }
  
  def modifySeq(insert:InsertAtChange) {
    withTestPartition {
	  Observers.currentMutator.withValue("server-application") {	// SOON this should be done by the framework
	    insert.target match {
		  case erasedSeq:SyncableSeq[_] => 
		    val seq = erasedSeq.asInstanceOf[SyncableSeq[Syncable]]
		    seq.insert(0, TestNameObj("val"))
		    val seq2 = createNameSequence("chris", "anya", "bryan") 
	     
		    seq.insert(0, seq2)
		  case _ => log.error("modifySeq() - not a SyncableSeq")
		}
	  }
    }      
  }  
  
  def twoSetChanged(change:ChangeDescription) {
    change match {
      case put:PutChange if (change.source != "server-application") => mirrorClientPut(put)
      case _ => // ignore other changes
    }
  }
  
  def mirrorClientPut(change:PutChange) {
      withTestPartition {
		Observers.currentMutator.withValue("server-application") {	// SOON this should be done by the framework
		  val name = new TestNameObj
		  name.name = "server-" + change.newValue.asInstanceOf[TestNameObj].name
		  change.target match {
		    case twoSet:SyncableSet[_] =>
		      twoSet.asInstanceOf[SyncableSet[Syncable]] += name       
		    case _ => 
		      log.error("twoSetChanged() unexpected target of change: " + change.target)
		    }
      }
    }
  }
}
