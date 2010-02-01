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
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.digiting.sync.syncable.TestNameObj
import ObserveUtil._ 

@RunWith(classOf[JUnitRunner])
class InstanceVersionTest extends Spec with ShouldMatchers {
  describe("InstanceVersion") {
    it("should update the version after a property change") {
      withTestEnvironment {
        val n = TestNameObj()
        n.version should be ("initial")
        n.name = "fred"
        n.version should not be ("initial")
      }
    }
    it("should update the version after a collection change") {
      withTestEnvironment {
        val s = new SyncableSeq[TestNameObj]()
        s.version should be ("initial")
        s += TestNameObj("sal")
        val v1 = s.version
        v1 should not be ("initial")
        s += TestNameObj("blueberry")
        s.version should not be (v1)
      }
    }
  }
}
