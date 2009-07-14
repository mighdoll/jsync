package com.digiting.sync.test
import com.jteigen.scalatest.JUnit4Runner
import org.junit.runner.RunWith
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import com.digiting.sync.syncable._
import ObserveUtil._

@RunWith(classOf[JUnit4Runner])
class ObservationTest extends Spec with ShouldMatchers {
  describe("Observation.watch") {
    it("should notice property change on an object") {
      val obj = new TestNameObj
      Observers.watch(obj, changed, this)
      obj.name = "MiloJ"  // should trigger an observable change

      assert (changes.length == 1)
      resetObserveTest()
    }
  }
  
  describe("Observation.watchDeep") {
    it("should stop watching objects that are no longer referenced") {
      val root = new TestRefObj
      val one = new TestNameObj
      root.reference = one
      Observers.watchDeep(root, changed, this)	// genreeates two watch change events
      root.reference = null	// generates on prop change, and one unwatch change
      changes foreach { change => Console println change}
      changes.size should be (4)	
      one.name = "one"	// should be outside the watched tree
      changes foreach { change => Console println change}
      changes.size should be (4)

      resetObserveTest()
    }
    it ("should see changes in a reference chain") {
      val obj = new TestRefObj()
      val obj2 = new TestRefObj()
      val obj3 = new TestRefObj()
      obj.reference = obj2			// two watch changes

      Observers.watchDeep(obj, changed, this)
      obj.reference = null			// unwatch + property change
      changes.size should be (4)  
      obj.reference = obj2			// watch + property change
      changes.size should be (6)    
      obj2.reference = obj3			// watch + property change
      changes.size should be (8)   
      resetObserveTest()
    }

    it("should see changes in a tree") {
      val root = new TestRefObj
      Observers.watchDeep(root, changed, this)	// watch
      val branch = new TestTwoRefsObj			
      val one,two = new TestNameObj
      root.reference = branch					// watch + prop change
      changes.size should be (3)
      branch.ref1 = one							// watch + property change
      changes.size should be (5)
      branch.ref2 = two							// watch + property change
      changes.size should be (7)
      branch.ref2 = null						// unwatch + property change
      changes.size should be (9)
      branch.ref2 = one							// property change
      changes.size should be (10)

      var watchCount = 0
      var propCount = 0
      var unwatchCount = 0
      for (change <- changes) {
        change match {
          case PropertyChange(_,_,_,_) =>
            propCount += 1
          case WatchChange(_,_) =>
            watchCount += 1
          case UnwatchChange(_,_) =>
            unwatchCount += 1
          case _ =>
        }
      }
      watchCount should be (4)
      propCount should be (5)
      unwatchCount should be (1)

      one.name = "one"	// property change (inside the tree)
      changes.size should be (11)
      two.name = "two"  // no chagne (should be outside the tree now)
      changes.size should be (11)

      resetObserveTest()
    }

    it("should find members referenced from a collection") {
      val set = new SyncableSet[Syncable]
      val name = new TestNameObj 
      set + name
      Observers.watchDeep(set, changed, "test")	// two watch changes
      name.name = "Ben"	// one property change
      changes.size should be (3)

      resetObserveTest()
    }
  }
}
