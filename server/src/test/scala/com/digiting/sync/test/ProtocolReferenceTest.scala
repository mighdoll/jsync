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
import ObserveUtil._
import com.digiting.util.Configuration
import net.lag.logging.Logger
import com.digiting.util.LogHelper
import com.digiting.sync.ResponseManager.AwaitResponse
import com.digiting.sync.test.ProtocolTester.callService

@RunWith(classOf[JUnit4Runner])
class ProtocolReferenceTest extends Spec with ShouldMatchers {
  describe("JsonSync") {    
    val log = Logger("ProtocolReferenceTest")
    it("should initialize configuration") {
      Configuration.initFromVariable("jsyncServerConfig")      
      TestApplication.registerTestServices(ProtocolReferenceTestServer)
    }
    
    it("should process references to objects within the transaction") {
      withTestEnvironment {
        val resultMap = 
          callService("ProtocolReferenceTestServer.serveTest", serveTestParameters) 
        resultMap map {_.get("name") should be (Some("success"))} orElse
          fail
      }
    }
  }
  
  def serveTestParameters():List[Syncable] = {
    val nameObj = new TestNameObj("fred")
    val refObj = new TestRefObj(nameObj)
    refObj :: Nil  
  }
  
}

@ImplicitServiceClass("ProtocolReferenceTestServer")
object ProtocolReferenceTestServer extends LogHelper {  
  val log = Logger("ProtocolReferenceTestServer")
  
  @ImplicitService
  def serveTest(ref:TestRefObj):Syncable = {
    App.withTransientPartition {
      ref.ref match {
        case name:TestNameObj if (name.name == "fred") =>
          log.debug("test succeeded")
          new TestNameObj("success")
        case _ =>
          log.error("test failed")
          new TestNameObj("failure")
      }
    }
  }
}