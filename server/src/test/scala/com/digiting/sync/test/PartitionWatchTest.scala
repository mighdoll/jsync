package com.digiting.sync.test
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.digiting.sync.syncable.TestNameObj

@RunWith(classOf[JUnitRunner])
class RamWatchesTest extends Spec with ShouldMatchers with SyncFixture {  
  describe("RamWatches") {
    it("should observe a change via watch()") {
      withTestFixture {
        val nameObj = new TestNameObj("jerome")
        SyncManager.instanceCache.commit()
        var found = false
        import testPartition._
        withTransaction {
          Observers.withMutator("testWatch") {
            watch(nameObj.id.instanceId, {change:DataChange =>
              change match {
                case PropertyChange(target, property, newValue, oldValue, versions) =>
                  found = true
                  property should be ("name")
                  oldValue.value should be ("jerome")
                  newValue.value should be ("murph")
                  target should be (nameObj.id)
                case _ =>
              }
            }, 100000)
          }
        }
        SyncManager.instanceCache.commit()
        nameObj.name = "murph"
        SyncManager.instanceCache.commit()
        found should be (true)
      }
    }
    it("should expire a change") {
      withTestFixture {
        val nameObj = new TestNameObj("jerome")
        SyncManager.instanceCache.commit()
        import testPartition._
        var found = false
        withTransaction {
          Observers.withMutator("testWatch") {
            watch(nameObj.id.instanceId, {_=>
              found = true},
            1)
          }
        }
        SyncManager.instanceCache.commit()
        withTempTx {tx =>
          val pickled = get(nameObj.id.instanceId, tx) getOrElse fail 
          pickled.watches.size should be (1)
        }
        Thread.sleep(2)
        nameObj.name = "murph"
        SyncManager.instanceCache.commit() 
        
        // expired watch should be cleaned out
        withTempTx {tx =>
          val pickled = get(nameObj.id.instanceId, tx) getOrElse fail 
          pickled.watches.size should be (0)
        }
        found should be (false)
        SyncManager.instanceCache.commit() 
      }
    }
  }
  import Partition.Transaction
  def withTempTx[T](fn:(Transaction)=>T):T = {
    val tx = new Transaction
    fn(tx)
  }
}
