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
        var found = false
        
        val nameObj = withTestPartition {TestNameObj("jerome")}
        withTempContext { // register watch from a separate context so that it will receive notifies 
          App.app.customObservePartition(nameObj.id) {changes:Seq[DataChange] =>
            changes match {
              case Seq(PropertyChange(target, property, newValue, oldValue, versions)) =>
                found = true
                property should be ("name")
                oldValue.value should be ("jerome")
                newValue.value should be ("murph")
                target should be (nameObj.id)
              case _ =>
            }
          }
        }      
        testApp.withApp {nameObj.name = "murph"}
        found should be (true)
      }
    }
    it("should expire a change") {
      withTestFixture {
        val nameObj = withTestPartition {TestNameObj("jerome")}
        import testPartition._
        var found = false
        withTempContext { // register watch from a separate context so that it will receive notifies 
          App.app.customObservePartition(nameObj.id, 1) {_=>
            found = true
          }
        }
        withTempTx {tx =>
          val pickled = get(nameObj.id.instanceId, tx) getOrElse fail 
          pickled.watches.size should be (1)
        }
        Thread.sleep(2)   // watch should timeout 
        withTestPartition {nameObj.name = "murph"}
        
        // expired watch should be cleaned out
        withTempTx {tx =>
          val pickled = get(nameObj.id.instanceId, tx) getOrElse fail 
          pickled.watches.size should be (0)
        }
        found should be (false)
      }
    }
  }
  import com.digiting.sync.Partition.Transaction
  def withTempTx[T](fn:(Transaction)=>T):T = {
    val tx = new Transaction(App.app.appId)
    fn(tx)
  }
}
