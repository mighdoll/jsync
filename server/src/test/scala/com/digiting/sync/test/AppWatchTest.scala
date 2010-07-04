package com.digiting.sync.test
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.digiting.sync.syncable.TestNameObj


@RunWith(classOf[JUnitRunner])
class AppWatchTest extends Spec with ShouldMatchers with SyncFixture {  
  describe("AppWatch") {
    it("should see a change") {      
      val shared = new RamPartition("shared")
      val app1 = TempAppContext("app1")
      val named = app1.withApp {
        shared.publish("name", TestNameObj("jerome"))
      }
      
      val app2 = TempAppContext("app2")
      app2.withApp {
      }
      
      named.name = "fred"
    }
  }
}