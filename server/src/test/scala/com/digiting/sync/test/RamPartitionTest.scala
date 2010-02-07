package com.digiting.sync.test
import com.digiting.sync.Pickled
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.digiting.sync.syncable.TestNameObj

@RunWith(classOf[JUnitRunner])
class RamPartitionTest extends Spec with ShouldMatchers with SyncFixture {
  describe("RamPartition") {
    it("should store and retrieve a seq") {
      withTestFixture {
        val s = new SyncableSeq[TestNameObj]
        s += new TestNameObj("Gavin")
        s += new TestNameObj("Quinn")
        SyncManager.instanceCache.commit()
        cleanup() // reset the local instance pool
        testPartition.get(s.fullId.instanceId) map {found:SyncableSeq[TestNameObj] =>
          found.length should be (2)
          found(0).name should be ("Gavin")
          found(1).name should be ("Quinn")
        } orElse {
          fail
        }
      }
    }
    it("should store and retrieve a set") {
      withTestFixture {
        val s = new SyncableSet[TestNameObj]
        s += new TestNameObj("Elle")
        s += new TestNameObj("Janet")
        SyncManager.instanceCache.commit()
        cleanup() // reset the local instance pool
        testPartition.get(s.fullId.instanceId) map {found:SyncableSet[TestNameObj] =>
          var foundJanet, foundElle = false
          found.size should be (2)
          for {name <- found} {
            if (name.name == "Elle") 
              foundElle = true
            else if (name.name == "Janet")
              foundJanet = true;            
          }
          foundJanet should be (true)
          foundElle should be (true)
        } orElse {
          fail
        }
      }
    }
    it("should store and retrieve a map") {
      withTestFixture {
        val s = new SyncableMap[String,TestNameObj]
        s("e") = new TestNameObj("Elle")
        s("j") = new TestNameObj("Janet")
        SyncManager.instanceCache.commit()
        cleanup() // reset the local instance pool
        testPartition.get(s.fullId.instanceId) map {found:SyncableMap[String,TestNameObj] =>
          found("e").name should be ("Elle")
          found("j").name should be ("Janet")
        } orElse {
          fail
        }
      }
    }
    
  }
}

