package com.digiting.sync.test
import com.digiting.sync.Pickled
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.digiting.sync.syncable.TestNameObj

@RunWith(classOf[JUnitRunner])
class RamPartitionTest extends Spec with ShouldMatchers with SyncFixture {
  
  private def commitTestSeq():SyncableSeq[TestNameObj] = {
    val s = new SyncableSeq[TestNameObj]
    s += new TestNameObj("Gavin")
    s += new TestNameObj("Quinn")
    SyncManager.instanceCache.commit()
    s
  }
  
  private def commitTestMap():SyncableMap[String, TestNameObj] = {
    val map = new SyncableMap[String,TestNameObj]
    map("e") = new TestNameObj("Elle")
    map("j") = new TestNameObj("Janet")
    SyncManager.instanceCache.commit()
    map
  }
                                  
  describe("RamPartition") {
    it("should store and retrieve a seq") {
      withTestFixture {
        val s = commitTestSeq()
        cleanup() // reset the local instance pool
        testPartition.get(s.fullId.instanceId) match {
          case Some(found:SyncableSeq[_]) =>
            val seq = found.asInstanceOf[SyncableSeq[TestNameObj]]
            seq.length should be (2)
            seq(0).name should be ("Gavin")
            seq(1).name should be ("Quinn")
          case _ =>
            fail
        }
      }
    }
    it("should move an element from a seq") {
      withTestFixture {
        val s = commitTestSeq()
        s.move(0, 1)
        SyncManager.instanceCache.commit()
        cleanup() // reset the local instance pool
        testPartition.get(s.fullId.instanceId) match {
          case Some(found:SyncableSeq[_]) =>
            val seq = found.asInstanceOf[SyncableSeq[TestNameObj]]
            seq.length should be (2)
            seq(1).name should be ("Gavin")
            seq(0).name should be ("Quinn")
          case _ =>
            fail
        }
      }
    }
    it("should remove an element from a seq") {
      withTestFixture {
        val s = commitTestSeq()
        s.remove(0)
        SyncManager.instanceCache.commit()
        cleanup() // reset the local instance pool
        testPartition.get(s.fullId.instanceId) match {
          case Some(found:SyncableSeq[_]) =>
            val seq = found.asInstanceOf[SyncableSeq[TestNameObj]]
            seq.length should be (1)
            seq(0).name should be ("Quinn")
          case _ =>
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
        testPartition.get(s.fullId.instanceId) match {
          case Some(found:SyncableSet[_]) =>
            val set = found.asInstanceOf[SyncableSet[TestNameObj]]
            var foundJanet, foundElle = false
            set.size should be (2)
            for {name <- set} {
              if (name.name == "Elle") 
                foundElle = true
              else if (name.name == "Janet")
                foundJanet = true;            
            }
            foundJanet should be (true)
            foundElle should be (true)
          case _ =>
            fail
        }
      }
    }
    
    it("should remove an element from a set") {
      withTestFixture {
        val s = new SyncableSet[TestNameObj]
        val beth = new TestNameObj("Beth")
        SyncManager.instanceCache.commit()
        s -= beth
        SyncManager.instanceCache.commit()
        cleanup() // reset the local instance pool
        testPartition.get(s.fullId.instanceId) match {
          case Some(found:SyncableSet[_]) =>
            val set = found.asInstanceOf[SyncableSet[TestNameObj]]
            set.size should be (0)
          case _ =>
            fail
        }
      }
    }
    
    it("should store and retrieve a map") {
      withTestFixture {
        val s = commitTestMap()
        SyncManager.instanceCache.commit()
        cleanup() // reset the local instance pool
        testPartition.get(s.fullId.instanceId) match {
          case Some(found:SyncableMap[_,_]) =>
            val map = found.asInstanceOf[SyncableMap[String,TestNameObj]]
            map("e").name should be ("Elle")
            map("j").name should be ("Janet")
          case _ =>
            fail
        }
      }
    }
    
    it("should remove an element from a map") {
      withTestFixture {
        val s = commitTestMap()
        SyncManager.instanceCache.commit()
        s -= ("e")
        SyncManager.instanceCache.commit()
        cleanup() // reset the local instance pool
        testPartition.get(s.fullId.instanceId) match {
          case Some(found:SyncableMap[_,_]) =>
            val map = found.asInstanceOf[SyncableMap[String,TestNameObj]]
            map get ("e") should be (None)
            map("j").name should be ("Janet")
          case _ =>
            fail
        }
      }
    }
    
  }
}

