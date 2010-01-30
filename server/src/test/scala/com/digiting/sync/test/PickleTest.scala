package com.digiting.sync.test
import com.digiting.sync.Pickled
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.digiting.sync.syncable.TestPrimitiveProperties
import com.digiting.sync.syncable.TestTwoRefsObj
import com.digiting.sync.test.ObserveUtil.withTestEnvironment

@RunWith(classOf[JUnitRunner])
class PickleTest extends Spec with ShouldMatchers {
  describe("Pickle") {
    it("should pickle primitives") {
      withTestEnvironment {
        val prim = new TestPrimitiveProperties
        prim.t = true
        prim.b  =1
        prim.s = 2
        prim.i = 3
        prim.l = 4
        prim.c = 'a'
        prim.f = 2.3f
        prim.d = .1
        val pickled = Pickled(prim)
        val prim2 = pickled.unpickle
        prim2.t should be (prim.t)
        prim2.b should be (prim.b)
        prim2.s should be (prim.s)
        prim2.i should be (prim.i)
        prim2.l should be (prim.l)
        prim2.c should be (prim.c)
        prim2.f should be (prim.f)
        prim2.d should be (prim.d)
      }
    }
    it("should pickle references") {
      withTestEnvironment {
        val a = new TestTwoRefsObj
        a.ref1 = a
        a.ref2 = null
        val b = Pickled(a).unpickle
        b.ref1 should be (b)
        b.ref2 should be (null)
      }
    }
  }
}
