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
package com.digiting.context.test
import com.jteigen.scalatest.JUnit4Runner
import org.junit.runner.RunWith
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import com.digiting.util.Configuration
import net.lag.logging._
import com.digiting.sync._
import com.digiting.sync.syncable.TestNameObj
import com.digiting.sync.syncable.TestRefObj
import com.digiting.sync.syncable.TestPrimitiveProperties
import SyncManager.withPartition
import org.scalatest.TestFailedException


@RunWith(classOf[JUnit4Runner])
class SimpleDbTest extends Spec with ShouldMatchers {
  val log = Logger("SimpleDbTest")
    
  var testPartition:SimpleDbPartition = null
  var admin:SimpleDbAdmin = null
  var testName:TestNameObj = null
  var testRef:TestRefObj = null
      
  def load[T](id:String):T = {
    testPartition get id match {
      case Some(ref) => ref.asInstanceOf[T]
      case _ => throw new TestFailedException("can't load TestRefObj id: " + id, 1)
    }
  }
  
  def createSet():SyncableSet[TestNameObj] = {
    withPartition(testPartition) { 
      val set = new SyncableSet[TestNameObj]
      set += new TestNameObj("xander")
      set += new TestNameObj("xander")
      set
    }
  }
  def createSeq():SyncableSeq[TestNameObj] = {
    withPartition(testPartition) { 
      val seq = new SyncableSeq[TestNameObj]
      seq += new TestNameObj("one")
      seq += new TestNameObj("two")
      seq
    }
  }
  
  def saveAndReload[T<:Syncable](syncable:T):T = {
    SyncManager.instanceCache.commit()
    SyncManager.instanceCache.remove(syncable)
    
    consistencySleep
    
	val loaded = load[T](syncable.id)    
    loaded
  }
  
  def consistencySleep() {
    log.trace("waiting..")
    Thread.sleep(3000)	// to work around simpledb eventual consistency
    log.trace("..waiting complete")    
  }
    

  describe("SimpleDbTest") {
    it("should initialize configuration") {
      SyncManager.reset()
      Configuration.init()
    }

    it("should create an admin") {
      val account = SimpleDbAccount("fix", "me")
      
      admin = new SimpleDbAdmin(account)
    }
    
    it("should delete the domain from previous tests") {
      admin.deleteDomain("testDomain")	      
    }
    
        
    it("should create a test partition") {
      testPartition = admin.partition(PartitionRef("testDomain", "sdb-test"))	// TODO create random number 
      admin.domains contains ("testDomain") should be (true)
      val partitionItem = admin.partitionsDomain item("testDomain.sdb-test")  
      partitionItem.attributes("partition").contains("sdb-test") should be (true)
      partitionItem.attributes("domain").contains("testDomain") should be (true)
    }
    
    
    it("should store/retrieve a simple syncable") {
      testName = withPartition(testPartition) { TestNameObj("fred") }
      val name = saveAndReload(testName)
      name.name should be (testName.name)
      SyncManager.instanceCache.commit()
    }    
    
    it("should store/retreive a syncable with a self reference") {
      testRef = withPartition(testPartition) { new TestRefObj() }
      testRef.ref = testRef
      
      val ref = saveAndReload(testRef)
      ref.ref should be (ref)
      ref.id should be (testRef.id)
    }
    
    
    it("should store and retrieve a syncable with a reference to another syncable") {
      val ref1 = withPartition(testPartition) { new TestRefObj() }
      val ref2 = withPartition(testPartition) { new TestRefObj() }
      
      ref1.ref = ref2
      ref2.ref = ref1
      
      val gotRef1 = saveAndReload(ref1)
      val ref1ref = gotRef1.ref.asInstanceOf[TestRefObj]
      ref1ref.id should be (ref2.id)
      ref1ref.ref.id should be (ref1.id)
    }
    
    it("should store primitive properties") {
      val p = withPartition(testPartition) { new TestPrimitiveProperties }
      p.b = 1
      p.s = 2002
      p.i = -1234684
      p.l = 9898989898L
      p.c = 'Z'
      p.t = true
      p.f = 3.14f
      p.d = java.lang.Math.E

      val loaded = saveAndReload(p)
      loaded.b should be (p.b)
      loaded.s should be (p.s)
      loaded.i should be (p.i)
      loaded.l should be (p.l)
      loaded.c should be (p.c)
      loaded.t should be (p.t)
      loaded.f should be (p.f)
      loaded.d should be (p.d)
    }

    
    it("should allow property modification") {
       val named = withPartition(testPartition) { TestNameObj("Bill") }
       SyncManager.instanceCache.commit()
       named.name = "Ygraine"
       val loaded = saveAndReload(named)
       loaded.name should be ("Ygraine")
    }

    it("should insert, save, and reload a seq") {
      val seq = createSeq()
	  val loaded = saveAndReload(seq)
	  seq.id should be (loaded.id)
	  seq should not equal (loaded)
      loaded.length should be (2)
      loaded(0).name should be ("one")
      loaded(1).name should be ("two")
    }
    
    it("should support move a seq") {
      val seq = createSeq()      
      SyncManager.instanceCache.commit()
      consistencySleep
      
      seq.move(0, 1)
      val loaded = saveAndReload(seq)
      loaded(0).name should be ("two")
      loaded(1).name should be ("one")
    }  
    
    it("should support remove in a seq") {
      val seq = createSeq()      
      SyncManager.instanceCache.commit()
      consistencySleep
      
      seq.remove(0)
      val loaded = saveAndReload(seq)
      loaded(0).name should be ("two")
    }
    
    it("should save a set") {
      val set = createSet()      
      val loaded = saveAndReload(set)
      
      loaded.size should be (2)
      loaded.foreach {elem => 
        elem.name should be ("xander")
      }
    }
    
    it("should remove one element from the set") {
      val set = createSet()
      SyncManager.instanceCache.commit()      
      consistencySleep
      set -= set.toStream.first 
      val loaded = saveAndReload(set)
      
      loaded.size should be (1)
      loaded.foreach {elem => 
        elem.name should be ("xander")
      }      
    }

    it("should remove all elements from the set") {
      val set = createSet()
      SyncManager.instanceCache.commit()      
      consistencySleep
      set.clear
      val loaded = saveAndReload(set)
      
      loaded.size should be (0)
    }
    
    
  }

}
