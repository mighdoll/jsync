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

@RunWith(classOf[JUnit4Runner])
class ProtocolTest extends Spec with ShouldMatchers {
  describe("JsonSync") {    
	val log = Logger("ProtocolTest")
    it("should initialize configuration") {
      Configuration.init()
    }
    it("should support subscribe on a simple object") {
      withTestEnvironment {
        import actors._
        import actors.Actor._
        val input = """ [
          {"#transaction":0},
          {"#start":true},
          {"kind":"$sync.subscription",
           "$partition":"test",
           "id":"Browser-0",
           "name":"oneName",
           "inPartition":"test",
           "root":null},
          {"#edit":
             { "id":"subscriptions",
        	   "$partition":".implicit"
             },
           "put":
             { "id":"Browser-0",
               "$partition":"test"
             }
          }
          ] """
        var found = false;
        Applications.deliver("sync"::Nil, input) foreach {app =>
          var gotIt = false
          var attempts = 0
          while (!found && attempts < 2) {
            attempts += 1
            found = checkResponse(app)
          }
        }
        
        found should be (true)
      
        import ResponseManager.AwaitResponse
        def checkResponse(app:AppContext):Boolean = {
          app.responses !?(1000, AwaitResponse(app.connection.debugId)) match {
            case Some(response:String) => response contains "emmett"
            case None => log.error("unexpected None response from AwaitResponse()")
              false
          }
        }
      None
      }
    }
  }
}
