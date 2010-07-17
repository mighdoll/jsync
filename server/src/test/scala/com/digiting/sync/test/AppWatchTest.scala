package com.digiting.sync.test
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.digiting.sync.syncable.TestNameObj
import com.digiting.sync.syncable.TestRefObj


@RunWith(classOf[JUnitRunner])
class AppWatchTest extends Spec with ShouldMatchers with SyncFixture {  
  describe("AppWatch") {
    it("should see a property change") {      
      val (app1, app2, copy1, copy2) = withTwoApps { TestNameObj("water") }
      copy1 should not be (copy2)
      copy2.name should be ("water")
              
      app1.withApp {
        copy1.name = "oil"
      }
      app2 !? Flush()
      copy2.name should be ("oil")
    }
    
    it("should see a property ref change") {
      val (app1, app2, copy1, copy2) = withTwoApps { 
        TestRefObj(TestNameObj("hummus")) 
      }
      copy2.ref.asInstanceOf[TestNameObj].name should be ("hummus")
      app1.withApp {
        copy1.ref = TestNameObj("tabouli")
      }
      app2 !? Flush()
      copy2.ref.asInstanceOf[TestNameObj].name should be ("tabouli")      
      copy2.ref should not be (copy1.ref)
    }
    
    it("should see seq changes") {
      val (app1, app2, copy1, copy2) = withTwoApps { 
        val seq = new SyncableSeq[TestRefObj[TestNameObj]]
        seq += TestRefObj(TestNameObj("zamboni"))
        seq 
      }
      copy2.first.ref.name should be ("zamboni")
      app1.withApp {
        copy1 += TestRefObj(TestNameObj("yamaha"))
      }
      app2 !? Flush()

      copy2(1).ref.name should be ("yamaha")
      app1.withApp {
        copy1.remove(0)
      }
      app2 !? Flush()
      copy2.length should be (1)      
    }
  }
  
  def withTwoApps[T <:Syncable](toPublish: =>T):(AppContext, AppContext, T, T) = {
    val shared = new RamPartition("shared")
    val app1 = TempAppContext("app1")
    val copy1 = app1.withApp {     
      val copy1 = toPublish
      shared.publish("sharedObj", copy1)
      copy1
    }
    
    val app2 = TempAppContext("app2")
    val copy2 = app2.withApp {
      val copy2 = shared.published find("sharedObj") map {
        _.asInstanceOf[T]
      } getOrElse fail
      app2.subscriptionService.active.subscribeRoot(copy2)
      copy2
    }    
    (app1, app2, copy1, copy2)
  }
}