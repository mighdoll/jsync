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
import com.digiting.sync.syncable._
import com.digiting.util.Configuration
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.digiting.sync.testServer.TestContext

  
@ImplicitServiceClass("SampleServices")
private object SampleServices {  
  var result = ""  
  
  @ImplicitService
  def oneStringReturnsSyncable(f:String):Syncable = {
    result = "oneStringReturnsSyncable"    
    null
  }
}

@RunWith(classOf[JUnitRunner])
class ServicesTest extends Spec with ShouldMatchers {  
  describe("AppContext") {
    it("should initialize configuration") {
      SyncManager.reset()
      Configuration.initFromVariable("jsyncServerConfig")      
    }
    it("should create implicit services") {
      val app = new TestContext(new Connection("ServicesTest"))
      app.createImplicitServices(SampleServices)      
    }
  }
}
