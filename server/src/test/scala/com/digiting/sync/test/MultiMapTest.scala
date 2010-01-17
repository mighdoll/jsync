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
package com.digiting.sync.test		// LATER move this to com.digting.util.test
import com.digiting.util.MultiMap
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
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
