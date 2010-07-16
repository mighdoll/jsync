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
import com.digiting.sync.syncable.TestPrimitiveProperties

/**
 * Setup a special partition that supports client tests.  The test partition includes
 * some published objects that the client can fetch to verify fetching from the server.  Some 
 * of the published objects respond to client changes to enable round trip testing.
 */
object TestSubscriptions extends LogHelper {
  lazy val log = logger("TestSubscriptions")
  private val partitionType = Configuration.getString("testPartition-type") getOrElse "RamPartition"
  private val testPartition = createPartition(partitionType, "test");
                                       
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
      primitivesRoundTrip()
    }
  }
  
  /** client views a single object */
  def oneName() {
    val id = SyncableId(testPartition.id, "#testName1")
    val name = SyncManager.withNextNewId(id) {
      TestNameObj("emmett")
    }
    testPartition.publish("oneName", name)
  }
  
  /** client views a single set */
  def oneSet() {
    val set = new SyncableSet[Syncable]
    set += TestNameObj("mercer")
    testPartition.publish("oneSet", set)
  }
  
  /** client modifies a simple object */
  def modifyOneName() {
    testPartition.publish("modifyOneName", new TestNameObj)
  }

  /** client and server modify objects that refer to each other.
   * server: root ->
   * client: root -> clientNewRef -> root   
   * server: root -> serverNewRef -> clientNewRef -> root
   */
  def modifyReference() {
    def modify(change:DataChange) {
      partialMatch(change) {
        case mod:PropertyChange =>  
          log.trace("modifyReference: %s", mod)
          mod.target.target orElse {abort("modifyReference")} foreach {found =>
            val root = found.asInstanceOf[TestRefObj[TestRefObj[_]]]
            val insertRef = new TestRefObj(root.ref)
            root.ref = insertRef
          }
      }
    }
    
    testPartition.publish("modifyReference") {
      val ref = new TestRefObj      
      withForeignChange(ref, "modifyReferenceTest") {modify}
      Some(ref)
    }    
  }
  
  /** server adds an object to a set when the client adds an object */
  def duplicatingSet() {
    def duplicate(set:SyncableSet[Syncable], change:DataChange) {
      partialMatch(change) {
        case put:PutChange =>
          App.app.withGetId(put.newVal) {newName =>
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
    
    testPartition.publish("duplicatingSet") {
      val set = new SyncableSet[Syncable]      
      withForeignChange(set, "duplicatingSetTest") {duplicate(set, _)}
      Some(set)
    }  
  }
  
  /** server and client insert and erase a sequence 
    * server: a,b,c
    * client: foo
    * server: (chris,anya,brian), val, foo
    */
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
    
    testPartition.publish("sequence") {
      val seq = createNameSequence("a","b","c")
      withForeignChange(seq, "sequenceTest") {modify}
      Some(seq)
    }
  }
  
  /** move an element in a sequence
   */
  def moveSequence() {    
    abcMatch("moveSequence") {
      case move:MoveChange =>
        val seq:SyncableSeq[Syncable] = expectSome(move.target.target)
        seq.move(2, 0)
    }
  }
  
  /** remove an element in a sequence
   */
  def removeSequence() {     
    abcMatch("removeSequence") {
      case remove:RemoveAtChange =>
        val seq:SyncableSeq[Syncable] = expectSome(remove.target.target)
        seq.remove(1)
    }
  }
  
  /** add a sequence as an element of another sequence
   */
  def addReferencedSequence() {
    def modified(change:DataChange) {
      partialMatch(change) {
        case p:PropertyChange => 
          val ref:TestRefObj[SyncableSeq[TestNameObj]] = expectSome(p.target.target)
          val seq = new SyncableSeq[TestNameObj]
          seq += new TestNameObj("don't duplicate me")
          ref.ref = seq
      }
    }
    testPartition.publish("addReferencedSequence") {
      val ref = new TestRefObj    
      withForeignChange(ref, "addReferencedSequence") {modified}
      Some(ref)
    }                                   
  }

  /** round trip modification of primitive field types */
  def primitivesRoundTrip() {
    
    testPartition.publish("primitivesRoundTrip") {
    	val prims = new TestPrimitiveProperties
      var once = false
      withForeignChange(prims, "primitivesRoundTrip") {change =>        
        partialMatch(change) {
          case p:PropertyChange => 
            if (!once) {
              once = true
              val prims:TestPrimitiveProperties = expectSome(p.target.target)
              prims.t = !prims.t
              prims.b = (prims.b + 1).toByte
              prims.s = (prims.s + 1).toShort
              prims.c = (prims.c + 1).toChar
              prims.l = prims.l + 1
              prims.i = prims.i + 1
              prims.f = prims.f + 1
              prims.d = prims.d + 1
            }
         }
      }
      Some(prims)
    }
  }
  
  /** a test on the sequence "a","b","c" */
  private def abcMatch[R](publicName:String)(pf:PartialFunction[DataChange,R]) {    
    generateAbc(publicName) {partialMatch(_)(pf)}
  }
  
  
  private def generateAbc(publicName:String)(modifiedFn:(DataChange)=>Unit) {
    testPartition.publish(publicName) {
      val seq = createNameSequence("a","b","c")
      withForeignChange(seq, publicName) {modifiedFn}
      Some(seq)
    }
  }
  
  /** cast an option expected to be of a certain type and non-empty
   * CONSIDER move this to util?  */
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
    App.app.watch2(syncable) {change =>
      if (change.source != App.currentAppName) {
        fn(change)
      } else {
//        log.trace("ignoring non-foreign change: %s", change)
      }
    }
  }
  
    /**
   * Utility to create a partition by class name.
   */
  private def createPartition(partitionType:String, name:String):Partition = {
    partitionType match {
      case "RamPartition" =>
        new RamPartition(name)
      case "InfinispanPartition" =>
        new InfinispanPartition(name)
      case x =>
        abort("unexptected partition type: ", x)
    }
  }
  
}
