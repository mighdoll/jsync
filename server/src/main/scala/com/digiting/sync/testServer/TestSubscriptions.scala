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
package com.digiting.sync.testServer
import com.digiting.sync.syncable._
import com.digiting.util._
import com.digiting.util.Matching._
import net.lag.logging.Logger
import com.digiting.sync.SyncManager.withGetId

/**
 * Setup a special partition that supports client tests.  The test partition includes
 * some published objects that the client can fetch to verify fetching from the server.  Some 
 * of the published objects respond to client changes to enable round trip testing.
 */
object TestSubscriptions extends LogHelper {
  val log = Logger("TestSubscriptions")
  val testPartition = new RamPartition("test")
  
  def init() {
    SyncManager.withPartition(testPartition) {
      oneName()
      oneSet()
      modifyOneName()
      duplicatingSet()
      modifyReference()    
      sequence()
      moveSequence()
      removeSequence()
      addReferencedSequence()
    }
  }
  
  def oneName() {
    val id = SyncableId(testPartition.partitionId, "#testName1")
    val name = SyncManager.withNextNewId(id) {
      new TestNameObj("emmett")
    }
    testPartition.publish("oneName", name)        
  }
  
  def oneSet() {
    val set = new SyncableSet[Syncable]
    set += TestNameObj("mercer")
    testPartition.publish("oneSet", set)
  }
  
  def modifyOneName() {
    testPartition.publish("modifyOneName", new TestNameObj)
  }

  /** modify reference chain:
   * root -> clientNewRef -> root   
   * root -> serverNewRef -> clientNewRef -> root
   */
  def modifyReference() {
    def modify(change:DataChange) {
      partialMatch(change) {
        case mod:PropertyChange if mod.newValue.value != null =>  // CONSIDER we get propChange to null, why? 
          log.trace("modifyReference: %s", mod)
          mod.target.target orElse {abort("modifyReference")} foreach {found =>
            val root = found.asInstanceOf[TestRefObj]
            val insertRef = new TestRefObj(root.ref)
            root.ref = insertRef
          }
      }
    }
    
    testPartition.publishGenerator("modifyReference", {()=>
      val ref = new TestRefObj      
      withForeignChange(ref, "modifyReferenceTest") {modify}
      Some(ref)
    })    
  }
  
  def duplicatingSet() {
    def duplicate(set:SyncableSet[Syncable], change:DataChange) {
      partialMatch(change) {
        case put:PutChange =>
          withGetId(put.newVal) {newName =>
            newName match {
              case clientName:TestNameObj =>
                set += new TestNameObj("server-" + clientName.name)
                log.trace("duplicatingSet() mirroring change to %s", clientName.name)
              case x =>
                abort("unexpected type in duplicatingSet: %s", x)
            }          
          }
      }
    }
    
    testPartition.publishGenerator("duplicatingSet", {()=>
      val set = new SyncableSet[Syncable]      
      withForeignChange(set, "duplicatingSetTest") {duplicate(set, _)}
      Some(set)
    })  
  }
  
  def sequence() {
    def modify(change:DataChange) {
      partialMatch(change) {        
        case insert:InsertAtChange =>
          val seq:SyncableSeq[Syncable] = expectSome(insert.target.target)
          seq.insert(0, TestNameObj("val"))
          val seq2 = createNameSequence("chris", "anya", "bryan")              
          seq.insert(0, seq2)              
      }
    }
    
    testPartition.publishGenerator("sequence", {() =>
      val seq = createNameSequence("a","b","c")
      withForeignChange(seq, "sequenceTest") {modify}
      Some(seq)
    })
  }
  
  def moveSequence() {    
    generatedAbcSequence("moveSequence") {
      case move:MoveChange =>
        val seq:SyncableSeq[Syncable] = expectSome(move.target.target)
        seq.move(2, 0)
    }
  }
  
  def removeSequence() {     
    generatedAbcSequence("removeSequence") {
      case remove:RemoveAtChange =>
        val seq:SyncableSeq[Syncable] = expectSome(remove.target.target)
        seq.remove(1)
    }
  }
  
  def addReferencedSequence() {
    def modified(change:DataChange) {
      partialMatch(change) {
        case p:PropertyChange => 
          val ref:TestRefObj = expectSome(p.target.target)
          val seq = new SyncableSeq[TestNameObj]
          seq += new TestNameObj("don't duplicate me")
          ref.ref = seq
      }
    }
    
    testPartition.publishGenerator("addReferencedSequence", {() =>
      val ref = new TestRefObj    
      withForeignChange(ref, "addReferencedSequence") {modified}
      Some(ref)
    })
                                   
  }
  
  
  private def generatedAbcSequence[R](publicName:String)(pf:PartialFunction[DataChange,R]) {
    def modified(change:DataChange) {
      partialMatch(change)(pf)
    }
    
    testPartition.publishGenerator(publicName, {() =>
      val seq = createNameSequence("a","b","c")
      withForeignChange(seq, "generatedAbcSequence") {modified}
      Some(seq)
    })        
  }
  
  
  /* CONSIDER move this to util?  */
  def expectSome[T](opt:Option[_]):T = {
    opt match {
      case Some(t) => 
        t.asInstanceOf[T]
      case _ =>
        abort("expectSome empty")
    }
  }
  
  
  private def createNameSequence(names: String*):SyncableSeq[TestNameObj] = {
    val seq = new SyncableSeq[TestNameObj]
    for (name <-names)
      seq += TestNameObj(name)    
    seq
  }

  
  /** call a function on changes made by the client */
  private def withForeignChange(syncable:Syncable, watchName:String)(fn: (DataChange)=>Unit)  {
    Observers.watch(syncable, watchName, {change =>
      if (change.source != App.currentAppName) {
        fn(change)
      } else {
//        log.trace("ignoring non-foreign change: %s", change)
      }
    })
  }
  
}
