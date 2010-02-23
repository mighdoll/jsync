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
import net.lag.logging.Logger
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import net.liftweb.json.JsonAST
import net.liftweb.json.JsonDSL._  
import com.digiting.sync.testServer.TestApplication

@RunWith(classOf[JUnitRunner])
class ProtocolTest extends Spec with ShouldMatchers {
  describe("JsonSync") {    
	val log = Logger("ProtocolTest")
  it("should initialize configuration") {
    Configuration.initFromVariable("jsyncServerConfig")      
  }
  it("should support subscribe on a simple object") {    
    val jsonMsg = """[
      {"#transaction":0},
      {"#start":
        {"appVersion":""" + "\"" + TestApplication.appVersion + """",
         "protocolVersion":""" + "\"" + ProtocolVersion.version + """",
         "authorization":""
        },
      },
      {"$kind":"$sync.subscription",
       "$partition":"test",
       "$id":"Browser-0",
       "name":"oneName",
       "inPartition":"test",
       "root":null
      },
      {"#edit":
         { "$id":"subscriptions",
    	   "$partition":".implicit"
         },
       "put":
         { "$id":"Browser-0",
           "$partition":"test"
         }
      }
    ] """
    
    def checkResponse(responseText:String):Option[Boolean] = {
      if (responseText contains "emmett") 
        Some(true)
      else
        None
    }
      
    val result = ProtocolFixture.sendTestMessage(jsonMsg, checkResponse)
      
    result should be (Some(true))
    }
  }
}
