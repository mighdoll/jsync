/*   Copyright [2009] Digiting Inc
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.digiting.sync.test
import com.digiting.sync.Pickled
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.digiting.sync.syncable.TestPrimitiveProperties
import com.digiting.sync.syncable.TestTwoRefsObj
import com.digiting.sync.syncable.TestNameObj

@RunWith(classOf[JUnitRunner])
class PickleTest extends Spec with ShouldMatchers with SyncFixture {
  describe("Pickle") {
    it("should pickle primitives") {
      withTestFixture {
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
        val prim2 = withTempContext {
          pickled.unpickle.asInstanceOf[TestPrimitiveProperties]
        }
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
      withTestFixture {
        val a = new TestTwoRefsObj
        a.ref1 = a
        a.ref2 = null
        val pickled = Pickled(a)
        val b = withTempContext {pickled.unpickle.asInstanceOf[TestTwoRefsObj]}
        b.ref1 should be (b)
        b.ref2 should be (null)
      }
    }
  }
}
