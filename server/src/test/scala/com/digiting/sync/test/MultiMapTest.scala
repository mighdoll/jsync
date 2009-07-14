package com.digiting.sync.test		// LATER move this to com.digting.util.test
import com.jteigen.scalatest.JUnit4Runner
import org.junit.runner.RunWith
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import com.digiting.util.MultiMap


@RunWith(classOf[JUnit4Runner])
class MultiMapTest extends Spec with ShouldMatchers {
  describe("MulitMap") {
    it("should support basic operations") {
      val map = new MultiMap[Int,String]
      map + (1,"foo")
      map - (2,"")
      map + (1,"foo")
      map - (1,"bar")
      map + (1,"cal")
      assert(!map.isEmpty)
      map - (1,"foo")
      assert(!map.isEmpty)
      map - (1,"cal")
      assert(map.isEmpty)
    }
  }
}
