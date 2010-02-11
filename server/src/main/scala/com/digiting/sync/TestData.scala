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
package com.digiting.sync
import com.digiting.sync.syncable._
import com.digiting.sync.aspects.Observable
import net.lag.logging.Logger
import Observers.watch
import SyncManager.withGetId
import com.digiting.util._

/** Embedded test server for client tests.  
 * SOON: refactor this into an application 
 */
object TestData {
  var log = Logger("TestData")
  var testPartition:Partition = _
  def setup() {
	testPartition = new RamPartition("test2")
    // setup up some static and dynamically genreated test objects
    withTestPartition {  		     
  	  testPartition.published.createGenerated("serverSequenceAdd", createSequenceAdd)  
    }
  }
  
  def withTestPartition[T](fn: => T):T = {
    SyncManager.currentPartition.withValue(testPartition) {
     fn
    }    
  }  
  
  def createSequenceAdd():Option[Syncable] = {
    val ref = withTestPartition { new TestRefObj }    
    watch(ref, "createSequenceMod", changed)    
    
    def changed(change:ChangeDescription) {
      if (change.source != "test-application") {
        log.trace("createSequenceAdd: %s", change)
        val seq = withTestPartition  {new SyncableSeq[TestNameObj]}
        Observers.currentMutator.withValue("test-application") {	// SOON this should be done by the framework
          ref.ref = seq
          seq += withTestPartition {new TestNameObj("don't duplicate me")}
        }
      }
    }
    
    Some(ref)
  }
  


  
  

  

}
