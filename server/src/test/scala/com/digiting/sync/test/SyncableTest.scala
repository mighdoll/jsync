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
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.Spec
import org.scalatest.SuperSuite
import com.digiting.sync._
import com.digiting.sync.aspects.Observable
import com.digiting.sync.syncable._
import com.digiting.util._
import collection._
import com.digiting.util.Configuration

@RunWith(classOf[JUnitRunner])
class SyncableTest extends Spec with ShouldMatchers with SyncFixture {
  describe("Syncable") {
    it("should allow setting an id") {
      withTestFixture {
        val obj = 
          SyncManager.withNextNewId(SyncableId(SyncManager.currentPartition.value.id, "foo")) {
            new SyncableSet
          }
        obj.id.instanceId.id should be ("foo")
        App.app.get(SyncManager.currentPartition.value.id.id, "foo") should be (Some(obj))
      } 
    }        
  }
}
