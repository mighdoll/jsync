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
  }
}

