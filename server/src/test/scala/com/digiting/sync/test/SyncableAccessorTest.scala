package com.digiting.sync.test
import com.jteigen.scalatest.JUnit4Runner
import org.junit.runner.RunWith
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import com.digiting.sync.syncable._
import ObserveUtil._


@RunWith(classOf[JUnit4Runner])
class SyncableAccessorTest extends Spec with ShouldMatchers {
  describe("A SyncableAccessor") {
    
    it("should find references from object properties") {
      val obj = new TestRefObj()
      val obj2 = new TestRefObj()
      obj.reference = obj2

      // verify internal accessor is built correctly
      val accessor = SyncableAccessor.get(obj.getClass)
      val refs = accessor.references(obj).toList

      refs.length should be (1)
      refs.head should be (obj2)
      resetObserveTest()
    }
  }
  
  it("should support setting a property") {
      val obj = new TestNameObj()
      obj.name = "Bruce"
      val accessor = SyncableAccessor.get(classOf[TestNameObj])
      accessor.set(obj, "name", "Fred")

      obj.name should be ("Fred")
      resetObserveTest
    }

}
