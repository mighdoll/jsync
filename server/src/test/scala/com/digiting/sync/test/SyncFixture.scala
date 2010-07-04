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
import scala.collection.mutable
import com.digiting.util.Configuration

object SyncFixture {
  var id:Int = -1
  def nextId():Int = {
    id = id + 1
    id
  }
}

import SyncFixture.nextId

class UnitTestContext(connection:Connection) extends AppContext(connection) {
  def appName = "Unit Test"
}

import SyncManager.withPartition

trait SyncFixture {  
  Configuration.initFromVariable("jsyncServerConfig")      
  val changes = new mutable.ListBuffer[ChangeDescription]()
  val testPartition = new RamPartition("testPartition-" + nextId()) with RamWatches
  val testApp = new UnitTestContext(new Connection("UnitTesting-Dummy"))
  
  def withTestFixture[T](fn: => T):T = {    
    setup()
  
    testApp.withApp {
      val result = withPartition(testPartition) { fn }
      cleanup()
      result
    }
  }
   
  def cleanup() {
    Observers.unwatchAll(this)
    Observers.reset()
    SyncManager.reset()
  }
  
  private def setup() {
    Configuration.initFromVariable("jsyncServerConfig")      
    changes.clear()
  }

    
  protected def changed(change:ChangeDescription) {
    changes + change
  }

}