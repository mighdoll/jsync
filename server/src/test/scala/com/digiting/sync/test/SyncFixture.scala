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


trait SyncFixture {  
  val changes = new mutable.ListBuffer[ChangeDescription]()
  val testPartition = new RamPartition("testPartition2")
  
  def withTestFixture[T](fn: => T):T = {
    setup()
    
    val result:T = fn
    cleanup()
    result
  }
   
  def cleanup() {
    Observers.unwatchAll(this)
    Observers.reset()
    SyncManager.reset()
  }
  
  private def setup() {
    Configuration.initFromVariable("jsyncServerConfig")      
    changes.clear()
    SyncManager.currentPartition.value = testPartition
  }

    
  protected def changed(change:ChangeDescription) {
    changes + change
  }

}