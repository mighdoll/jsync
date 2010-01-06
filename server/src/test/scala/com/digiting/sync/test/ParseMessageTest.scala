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


@RunWith(classOf[JUnit4Runner])
class ParseTest extends Spec with ShouldMatchers {
  describe("JsonSync") {
    it("should initialize configuration") {
      Configuration.initFromVariable("jsyncServerConfig")      
    }
    it("should convert maps to messages and back") {
      withTestEnvironment {
        // create message manually
        val syncs = ImmutableJsonMap("id" -> "test-2", "$partition" -> "test", "name" -> "Sandi" ) :: Nil
        val puts = ImmutableJsonMap("id" -> "test-2", "$partition" -> "test") :: Nil
        val edits = ImmutableJsonMap("#edit" -> ImmutableJsonMap("id" -> "test-2", "$partition" -> "test"), 
                                     "put" -> puts) :: Nil
        val controls = ImmutableJsonMap("#start" -> true) :: Nil
        val message = new Message(0, controls, edits, syncs)
        
        // message to json and back
        val json = message.toJson
        val parsed = ParseMessage.parse(json)
        parsed.length should be (1)
        parsed match {
          case (parsedMessage:Message) :: Nil => 
            val roundTripJson = parsedMessage.toJson
            roundTripJson should be (json)
          case _ =>
        }
      }
    }
  }    
}
