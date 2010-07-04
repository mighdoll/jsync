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
package com.digiting.sync.testServer
import com.digiting.util.LogHelper
import net.lag.logging.Logger
import net.lag.logging.Logger
import com.digiting.sync.syncable.TestNameObj

object ClientTestApp {
  def init() { 
    TestApplication.registerTestServices(ClientTestResponse)
  }
}

// TODO add a client test that calls this
@ImplicitServiceClass("ClientTestResponse")
object ClientTestResponse extends LogHelper {  
  val log = Logger("ClientTestResponse")
  
  @ImplicitService
  def returnBla(ref:Syncable):Syncable = {
    App.withTransientPartition {
      TestNameObj("bla")
    }
  }
}
