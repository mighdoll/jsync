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

import com.jteigen.scalatest.JUnit4Runner
import org.junit.runner.RunWith
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.Spec
import org.scalatest.SuperSuite
import com.digiting.sync._
import com.digiting.sync.aspects.Observable
import com.digiting.sync.syncable._
import com.digiting.util._
import collection._
import ObserveUtil._
import com.digiting.util.Configuration

@RunWith(classOf[JUnit4Runner])
class SyncableTest extends Spec with ShouldMatchers {
  describe("Syncable") {
    it("should initialize configuration") {
      Configuration.initFromVariable("jsyncServerConfig")      
    }
    it("should allow setting an id") {
      withTestEnvironment {
        val obj = 
          SyncManager.setNextId.withValue(SyncableIdentity("foo", SyncManager.currentPartition.value)) {
            new SyncableSet
          }
        obj.id should be ("foo")
        SyncManager.get(SyncManager.currentPartition.value.partitionId, "foo") should be (Some(obj))
      } 
    }        
  }
}
