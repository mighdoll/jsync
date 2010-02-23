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
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import com.digiting.sync.syncable._
import com.digiting.util.Configuration


@RunWith(classOf[JUnitRunner])
class SyncableAccessorTest extends Spec with ShouldMatchers with SyncFixture {
  describe("A SyncableAccessor") {
    
    it("should initialize configuration") {
      Configuration.initFromVariable("jsyncServerConfig")      
    }
    
    it("should find references from object properties") {
      withTestFixture {
        val obj = new TestRefObj()
        val obj2 = new TestRefObj()
        obj.ref = obj2

        // verify internal accessor is built correctly
        val accessor = SyncableAccessor.get(obj.getClass)
        val refs = accessor.references(obj).toList

        refs.length should be (1)
        refs.head should be (obj2)
      }
    }
  }
  
  it("should support setting a property") {
      withTestFixture {
        val obj = new TestNameObj()
        obj.name = "Bruce"
        val accessor = SyncableAccessor.get(classOf[TestNameObj])
        accessor.set(obj, "name", "Fred")

        obj.name should be ("Fred")
      }
    }

}
