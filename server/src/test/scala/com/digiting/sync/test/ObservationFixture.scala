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
import collection._
import com.digiting.sync.SyncManager
import com.digiting.util.Configuration


/* LATER: make this an instance (not a global) if we ever want to run tests in parallel.. */
object ObserveUtil {
  val changes = new mutable.ListBuffer[ChangeDescription]()
  def changed(change:ChangeDescription) {
    changes + change
  }
  val testPartition = new RamPartition("testPartition")

  def cleanup() {
    Observers.unwatchAll(this)
    Observers.reset()
    SyncManager.reset()
  }
  
  def setup() {
    Configuration.initFromVariable("jsyncServerConfig")      
    changes.clear()
    SyncManager.currentPartition.value = testPartition
  }
  
  def withTestEnvironment[T](fn: => T):T = {
    setup()
    
    val result:T = fn
    cleanup()
    result
  }
}
