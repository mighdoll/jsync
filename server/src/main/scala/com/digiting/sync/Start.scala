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
import com.digiting.util.Configuration
import com.digiting.sync.testServer.ClientTestApp
import com.liquidj.site.Demos

object Start {
  def start() {
    Configuration.initFromVariable("jsyncServerConfig")
    Configuration.getString("testServer") foreach {_=>
      ClientTestApp.init()          
    }
    
    Demos.init  // SOON move this to a separate project
  }
}
