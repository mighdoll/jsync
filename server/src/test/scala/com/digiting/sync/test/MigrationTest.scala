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
import com.digiting.sync.syncable._
import com.digiting.util.Configuration
import com.digiting.sync.SyncableSerialize._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class MigrationTest extends Spec with ShouldMatchers with SyncFixture {
  describe("Migration") {
    it("should initialize configuration") {
      Configuration.initFromVariable("jsyncServerConfig")      
    }
    
    it("should migrate a simple object from an old to a new schema") {
      withTestFixture {
        val pickled = withTestPartition {
          val old = new KindVersion0
          val ref = TestNameObj("wheel")
          old.obj = new TestNameObj("wheel")
          Pickled(old)
        }
        pickled
        
        withTempContext {
          pickled.unpickle() match {
            case migrated:KindVersion =>
              migrated.kind should be (pickled.reference.kind)
              migrated.kindVersion should be ("1")
              migrated.obj.ref.asInstanceOf[TestNameObj].name should be ("wheel")
            case _ =>
              fail            
          }
        }      
      } 
    }        
  }
}
