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
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import com.digiting.util.Configuration
import net.lag.logging.Logger
import net.lag.configgy.Configgy
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ConfigurationTest extends Spec with ShouldMatchers {
  describe("Configuration") {
    def withBaseConfiguration[T](fn: =>T):T = {
       Configuration.initFromVariable("jsyncServerConfig")      
       val result = fn
       Configuration.reset()
       result
    }
    
    it("should load configuration in debug mode") {
      withBaseConfiguration {
        Configuration.withOverride("jsyncServer_runMode" -> None) {
          Configuration.reset()
          Configuration("digiting") should be ("true")
          Configuration.runMode should be ("debug")
        }
      }
    }
    it("should configuration in productionTest mode") {
      withBaseConfiguration {
        Configuration.withOverride("jsyncServer_runMode" -> Some("productionTest")) {
          Configuration.reset()
          Configuration("digiting") should be ("true")       
          Configuration.runMode should be ("productionTest")
        }
      }
    }
    
  }
}

