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
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import com.digiting.sync.syncable._
import com.digiting.util.Configuration
import ObserveUtil.withTestEnvironment
import com.digiting.sync.SyncableSerialize._

@RunWith(classOf[JUnit4Runner])
class MigrationTest extends Spec with ShouldMatchers {
  describe("Migration") {
    it("should initialize configuration") {
      Configuration.initFromVariable("jsyncServerConfig")      
    }
    
    it("should migrate a simple object from an old to a new schema") {
      withTestEnvironment {
        val old = new KindVersion0
        old.ref = new TestNameObj("wheel")
        val oldAttributes = syncableAttributes(old)
        
        val created = createFromAttributes(old.id, old.partition, oldAttributes)
        created match {
          case Some(migration:Migration[_]) =>
            migration.migrate match {
              case migrated:KindVersion =>
                migrated.kind should be (old.kind)
                migrated.kindVersion should be ("1")
                migrated.ref.ref should be (old.ref)
              case _ =>
                assert(false)
            }
          case _ =>
            assert(false)
        }
      } 
    }        
  }
}
