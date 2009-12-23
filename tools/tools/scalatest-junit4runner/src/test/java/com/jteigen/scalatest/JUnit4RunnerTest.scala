package com.jteigen.scalatest

import org.junit.runner.{RunWith, JUnitCore}
import org.junit.Test
import org.junit.Assert.assertEquals
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.{FunSuite, Spec}

object SuitesAndSpecs {
  @RunWith(classOf[JUnit4Runner])
  class SomeFunSuite extends FunSuite {
    test("it runs"){
      assert(1 === 1)
    }

    test("it fails"){
      assert(2 === 1)
    }

    ignore("never mind"){
      assert(2 === 3)
    }
  }

  @RunWith(classOf[JUnit4Runner])
  class SomeSpec extends Spec with ShouldMatchers {
    describe("a running spec"){
      it("should run"){
        2 should be(2)
      }

      it("should fail"){
        2 should be(3)
      }

      ignore("should ignore"){
        2 should be(3)
      }
    }
  }
}
class JUnit4RunnerTest {
  import SuitesAndSpecs._

  @Test
  def runWithJunit {
    val result = JUnitCore.runClasses(classOf[SomeFunSuite], classOf[SomeSpec])
    assertEquals(4, result.getRunCount)
    assertEquals(2, result.getIgnoreCount)
    assertEquals(2, result.getFailureCount)
  }
}
