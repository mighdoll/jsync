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
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.digiting.util.Log2._

@RunWith(classOf[JUnitRunner])
class MigrationTest extends Spec with ShouldMatchers with SyncFixture {
  implicit private lazy val log = logger("MigrationTest")
  describe("Migration") {
    it("should initialize configuration") {
      Configuration.initFromVariable("jsyncServerConfig")      
    }
    
    it("should migrate a simple object from an old to a new schema") {
      def expectTestPartitionVersion(id:SyncableId, version:String) {
        testPartition.withDebugTransaction(App.app.appId) {tx =>     // verify that partition has the updated versio now
          testPartition.get(id.instanceId, tx) match {
            case Some(pickled:Pickled) => 
              pickled.id.kindVersion should be (version)          
            case _ => fail  
          }
        }        
      }
      
      withTestFixture {
        val old = withTestPartition { KindVersion0(TestNameObj("wheel")) }
        expectTestPartitionVersion(old.id, "0") // orig version in store
        
        withTempContext {
          testPartition.get(old.id.instanceId) match {  // triggers the migration (via unpickle)
            case Some(migrated:KindVersion) =>
              migrated.kind should be (old.kind)
              migrated.kindVersion should be ("1")
              migrated.obj.ref.name should be ("wheel")
            case _ =>
              fail
          }
        } 
        
        expectTestPartitionVersion(old.id, "1") // new version in store
        
      } 
    }   
  }
}
